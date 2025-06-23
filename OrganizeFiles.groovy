import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.Directory
import com.drew.metadata.Metadata
import com.drew.metadata.Tag
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.file.FileSystemDirectory
import com.fasterxml.jackson.databind.ObjectMapper
import groovy.io.FileType
import groovy.transform.CompileStatic

import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files

import static LogTypes.debug
import static LogTypes.error
import static LogTypes.info
import static LogTypes.trace
import static LogTypes.warning
import static groovyx.gpars.GParsPool.withPool

@CompileStatic
@Grab('org.codehaus.gpars:gpars:1.2.1')
@Grab('com.drewnoakes:metadata-extractor:2.15.0')
class OrganizeFiles {
    final static Set<String> ALLOWED_EXTENSIONS = ['.jpg', '.jpeg', '.gif', '.heic', '.bmp', '.png', '.tiff', '.mpg', '.mpeg', '.mp4', '.avi', '.mov', '.3gp'] as Set
    final static String EXTENSION_PATTERN = /.*\.[jpg|JPG|jpeg|JPEG|gif|GIF|heic|HEIC|bmp|BMP|png|PNG|tiff|TIFF|mpg|MPG|mpeg|MPEG|mp4|MP4|avi|AVI|mov|MOV|3gp|3GP]+/
    final static Set<String> EXCLUDE_FILENAME_STARTS = ['.', '_vti_cnf', 'organized', 'unsorted', 'derivatives'] as Set
//    final static String ORIGIN_FOLDER = '/Users/erikmason/Documents/pics'
//    final static String DESTINATION_FOLDER = '/Users/erikmason/Documents/pics'
    final static String ORIGIN_FOLDER = '/Volumes/EasyStore/otherDrive'
    final static String DESTINATION_FOLDER = '/Volumes/EasyStore'
    final static LogTypes LOGGING_LEVEL = info
    OrganizationExecution execData
    ObjectMapper objectMapper = new ObjectMapper()

    void execute() {
        loadCurrentExecution()
        if (execData.toOrganize.size() == 0) {
            findAllFiles()
        }
        processFiles()
    }

    void clearProgress() {
        log(info, 'Clearing organization progress...')
        loadCurrentExecution()
        log(debug, execData.toString())
        File organizedFiles = new File("$execData.destinationFolder/organized")
        if(organizedFiles.exists()) {
            log(info, "Deleting $organizedFiles.path...")
            organizedFiles.deleteDir()
        }
        execData.toOrganize.each { FileData file ->
            file.organized = false
        }
        saveState()
        log(info, 'Done clearing progress.')
    }

    void processFiles() {
        log(info, 'Beginning organization process!')
        withPool(50) { it ->
            execData.toOrganize.each { FileData file ->
                processFile(file)
                printUpdate()
                if(execData.organized % 50 == 0) {
                    saveState()
                }
            }
        }
    }

    synchronized void saveState() {
        File executionFile = new File("$DESTINATION_FOLDER/execution.json")
        objectMapper.writeValue(executionFile, execData)
    }

    void printUpdate() {
        long processed = execData.organized
        long remaining = execData.toOrganize.size() as long - processed
        long elapsedTime = System.nanoTime() - execData.startTime
        double percentDone = Math.round((processed as double) / (execData.toOrganize.size() as double) * 100000d) / 1000d
        long elapsedInSeconds = (Math.round((elapsedTime as double) / 1000000000d) + 1)
        long timeRemaining = Math.round((elapsedInSeconds as double) / (processed as double) * (remaining as double))
        long ratio = Math.round((processed as double) / (elapsedInSeconds as double) * 60d)
        CharSequence message = "\r$percentDone%     Created $execData.foldersCreated folders.     Processed $processed files.     Found $execData.duplicates duplicates.     Elapsed time: $elapsedInSeconds seconds     ETA: $timeRemaining seconds     Averages $ratio files per minute          "
        log(info, message, false)
    }

    void processFile(FileData file) {
        if (file.organized) {
            return
        }
        applyDestinationFilePath(file)
        if(file.duplicate) {
            return
        }
        log(debug, "Gonna check that $file.destinationFolder exists...")
        File folder = new File(file.destinationFolder)
        if (!folder.exists()) {
            log(debug, "Making directory $file.destinationFolder")
            execData.foldersCreated++
            folder.mkdirs()
        }
        log(debug, "Copying $file.filename to $file.destinationFilename")
        try {
            //I honestly don't know why I have copy commented and move not
//            Files.copy(new File(file.fullPath).toPath(), new File(file.destinationFilename).toPath())
            Files.move(new File(file.fullPath).toPath(), new File(file.destinationFilename).toPath())
            file.organized = true
            execData.organized++
            execData.timeStamp = System.nanoTime()
        } catch (FileAlreadyExistsException exception) {
            StringWriter sw = new StringWriter()
            PrintWriter pw = new PrintWriter(sw)
            exception.printStackTrace(pw)
            log(trace, sw.toString())
            log(error, "Caught FileAlreadyExistsException for $exception.message")
        }
    }

    void applyDestinationFilePath(FileData fileData) {
        String filePath
        if (fileData.originalDate == null) {
            log(debug, "Original date not found for file $fileData.fullPath")
            filePath = "$DESTINATION_FOLDER/organized/unsorted"
        } else {
            filePath = "$DESTINATION_FOLDER/organized/${fileData.originalDate.format('yyyy')}/${fileData.originalDate.format('MM')}"
        }
        fileData.destinationFolder = filePath
        File destinationFile = new File("$filePath/$fileData.filename-$fileData.fileSize.$fileData.fileExtension")
        if (destinationFile.exists()) {
            fileData.duplicate = true
            execData.duplicates++
        }
        fileData.destinationFilename = destinationFile.path
    }

    void loadCurrentExecution() {
        File executionFile = new File("$DESTINATION_FOLDER/execution.json")
        if (executionFile.exists()) {
            log(info, 'Found in progress organization batch, continuing to organize...')
            execData = objectMapper.readValue(executionFile, OrganizationExecution)
        } else {
            execData = new OrganizationExecution(originFolder: ORIGIN_FOLDER, destinationFolder: DESTINATION_FOLDER, loggingLevel: LOGGING_LEVEL)
        }
        execData.startTime = System.nanoTime() - execData.timeStamp
    }

    boolean shouldProcessFile(File file) {
        boolean result = true
        log(debug, "File hidden? ${file.isHidden()}")
        result = result && !file.isHidden()
        log(debug, "File $file.name matches pattern? ${file.name ==~ EXTENSION_PATTERN}")
        result = result && file.name ==~ EXTENSION_PATTERN
        EXCLUDE_FILENAME_STARTS.each { String excludeMe ->
            file.name.split('/').each { String filePart ->
                log(debug, "File name startsWith $excludeMe? ${filePart.startsWithIgnoreCase(excludeMe)}")
                result = result && !filePart.startsWithIgnoreCase(excludeMe)
            }
        }
        return result
    }

    @SuppressWarnings('GroovyVariableNotAssigned')
    Date extractFileDate(File file) {
        //need to review this code
        Metadata metadata
        try {
            metadata = ImageMetadataReader.readMetadata(file)
        } catch (Exception exception) {
            log(trace, "Caught $exception.class.simpleName for ${file.toURI()}")
        }
        metadata?.directories?.each { Directory directory ->
            directory.tags.each { Tag tag ->
                log(trace, tag as String)
            }
        }
        ExifSubIFDDirectory directory = metadata?.getFirstDirectoryOfType(ExifSubIFDDirectory)
        Date originalDate
        if (directory) {
            originalDate = directory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)
        } else {
            FileSystemDirectory fileSystemDirectory = metadata?.getFirstDirectoryOfType(FileSystemDirectory)
            if (fileSystemDirectory) {
                log(debug, "Could not find original date, trying to find file modified date tag for $file.path.")
                originalDate = fileSystemDirectory.getDate(FileSystemDirectory.TAG_FILE_MODIFIED_DATE)
            } else {
                log(debug, "Could not find file modified tag for image $file.path, will use lastModified.")
                originalDate = new Date(file.lastModified())
            }
        }
        return originalDate
    }

    void findAllFiles() {
        log(info, "Finding files of type $ALLOWED_EXTENSIONS...")
        File directory = new File(ORIGIN_FOLDER)
        directory.eachFileRecurse(FileType.FILES) { File file ->
            log(debug, "Now looking at file $file.path")
            if (shouldProcessFile(file)) {
                log(debug, "Will process file $file.path")
                String filename = file.name.split('/').last()
                FileData image = new FileData(
                        fullPath: file.path,
                        filename: filename.split('\\.').first(),
                        fileExtension: filename.split('\\.').last(),
                        fileSize: file.length(),
                        originalDate: extractFileDate(file)
                )
                if (!execData.toOrganize.contains(image)) {
                    execData.toOrganize.add(image)
                }
            }
            log(info, "Found $ANSI_PURPLE${execData.toOrganize.size()}$ANSI_RESET files so far...", false)
        }
        log(info, "Found $ANSI_PURPLE${execData.toOrganize.size()}$ANSI_RESET files.")
        saveState()
    }

    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BLACK = "\u001B[30m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_PURPLE = "\u001B[35m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_WHITE = "\u001B[37m";

    static String colorForLog(LogTypes type) {
        switch (type) {
            case trace:
                return ANSI_PURPLE
            case debug:
                return ANSI_CYAN
            case warning:
                return ANSI_YELLOW
            case info:
                return ANSI_WHITE
            case error:
                return ANSI_RED
        }
    }

    void log(LogTypes type, CharSequence message, boolean carriageReturn = true) {
        CharSequence coloredText = "${colorForLog(type)}$message$ANSI_RESET"
        if (execData.loggingLevel.shouldLog(type)) {
            if (carriageReturn) {
                System.out.println(coloredText)
            } else {
                System.out.print("\r$coloredText")
            }
        }
    }
}
