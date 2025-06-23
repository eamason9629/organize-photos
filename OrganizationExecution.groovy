import groovy.transform.CompileStatic
import groovy.transform.ToString

@CompileStatic
@ToString(excludes = ['toOrganize'], includeNames = true)
class OrganizationExecution {
    String originFolder
    String destinationFolder
    int foldersCreated
    LogTypes loggingLevel
    long timeStamp
    long startTime
    long duplicates
    long organized
    Set<FileData> toOrganize = []
}