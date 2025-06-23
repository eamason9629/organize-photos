import groovy.transform.CompileStatic
import groovy.transform.ToString

@CompileStatic
@ToString(includeNames = true)
class FileData {
    String fullPath             //original file full path, including filename
    String filename             //file name "photo1"
    String fileExtension        //the extension of the file
    long fileSize               //how large the file is in bytes
    String destinationFolder    //the folder the file will go to
    String destinationFilename  //the new file name of the file
    Date originalDate           //the original date the file was create
    boolean organized           //has the file been organized?
    boolean duplicate           //does the file have duplicates?

    @Override
    boolean equals(Object other) {
        return (this.is(other) || (this.class.is(other?.class) && fullPath == (other as FileData).fullPath))
    }

    @Override
    int hashCode() {
        return Objects.hashCode(fullPath)
    }
}