import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static LogTypes.debug
import static LogTypes.error
import static LogTypes.info
import static LogTypes.trace
import static LogTypes.warning

@Grab('org.spockframework:spock-core:2.0-groovy-3.0')
@Grab('org.objenesis:objenesis:3.1')
@Grab('cglib:cglib-nodep:3.3.0')
class OrganizeFilesTest extends Specification {
    @Subject
    OrganizeFiles sut = new OrganizeFiles(execData: new OrganizationExecution(loggingLevel: error))
//    OrganizeFiles sut = new OrganizeFiles(execData: new OrganizationExecution(loggingLevel: debug))

    @Unroll
    def 'printUpdate- #expected'() {
        setup:
        OrganizeFiles localSut = Spy(OrganizeFiles)
        localSut.execData = Spy(OrganizationExecution)
        localSut.execData.startTime = 0
        localSut.execData.foldersCreated = 10
        localSut.execData.organized = 5
        localSut.execData.duplicates = 2
        localSut.execData.toOrganize = [
                new FileData(fullPath: '1', duplicate: true, organized: true),
                new FileData(fullPath: '2', duplicate: true, organized: true),
                new FileData(fullPath: '3', organized: true),
                new FileData(fullPath: '4', organized: true),
                new FileData(fullPath: '5', organized: true),
                new FileData(fullPath: '6'),
                new FileData(fullPath: '7'),
                new FileData(fullPath: '8'),
                new FileData(fullPath: '9'),
                new FileData(fullPath: '10')
        ] as Set

        when:
        localSut.printUpdate()

        then:
        _ * localSut.execData.startTime >> { System.nanoTime() - (elapsedTimeSeconds * 1000000000l) }
        1 * localSut.log(info, _, false) >> { arguments ->
            assert arguments[1] == expected
        }

        where:
        elapsedTimeSeconds | expected
        1l                 | '\r50.0%     Created 10 folders.     Processed 5 files.     Found 2 duplicates.     Elapsed time: 2 seconds     ETA: 2 seconds     Averages 150 files per minute          '
        2l                 | '\r50.0%     Created 10 folders.     Processed 5 files.     Found 2 duplicates.     Elapsed time: 3 seconds     ETA: 3 seconds     Averages 100 files per minute          '
        100l               | '\r50.0%     Created 10 folders.     Processed 5 files.     Found 2 duplicates.     Elapsed time: 101 seconds     ETA: 101 seconds     Averages 3 files per minute          '
        500l               | '\r50.0%     Created 10 folders.     Processed 5 files.     Found 2 duplicates.     Elapsed time: 501 seconds     ETA: 501 seconds     Averages 1 files per minute          '
        1000l              | '\r50.0%     Created 10 folders.     Processed 5 files.     Found 2 duplicates.     Elapsed time: 1001 seconds     ETA: 1001 seconds     Averages 0 files per minute          '
        10000l             | '\r50.0%     Created 10 folders.     Processed 5 files.     Found 2 duplicates.     Elapsed time: 10001 seconds     ETA: 10001 seconds     Averages 0 files per minute          '
    }

    @Unroll
    def 'shouldProcessFile - #desc'() {
        setup:
        File given = Mock(File)

        when:
        boolean result = sut.shouldProcessFile(given)

        then:
        result == expected
        given.name >> fileName
        given.isHidden() >> hidden

        where:
        expected | fileName                             | hidden | desc
        true     | '/some/random/filename.jpg'          | false  | 'Totally valid jpg'
        false    | '/some/random/filename.jpg'          | true   | 'Totally valid, but hidden'
        true     | '/some/random/filename.jpeg'         | false  | 'Totally valid jpeg'
        true     | '/some/random/filename.gif'          | false  | 'Totally valid gif'
        true     | '/some/random/filename.heic'         | false  | 'Totally valid heic'
        true     | '/some/random/filename.mpg'          | false  | 'Totally valid mpg'
        true     | '/some/random/filename.mpeg'         | false  | 'Totally valid mpeg'
        true     | '/some/random/filename.mp4'          | false  | 'Totally valid mp4'
        true     | '/some/random/filename.avi'          | false  | 'Totally valid avi'
        true     | '/some/random/filename.mov'          | false  | 'Totally valid mov'
        false    | '/some/random/filename.pdf'          | false  | 'Do not want'
        false    | '/some/random/filename.pdf'          | true   | 'Do not want and hidden'
        false    | '/some/random/.folder/filename.jpg'  | false  | 'folder starts with .'
        false    | '/some/random/_vti_cnf/filename.jpg' | false  | '_vti_cnf folder'
        false    | '/some/organized/filename.jpg'       | false  | 'already organized'
        false    | '/some/unsorted/filename.jpg'        | false  | 'already organized, but unsorted'

    }

    @Unroll
    def 'shouldLog - #logLevel + #given => #expected'() {
        expect:
        logLevel.shouldLog(given) == expected

        where:
        logLevel | given   | expected
        error    | null    | false
        error    | error   | true
        error    | warning | false
        error    | info    | true
        error    | debug   | false
        error    | trace   | false
        warning  | null    | false
        warning  | error   | true
        warning  | warning | true
        warning  | info    | true
        warning  | debug   | false
        warning  | trace   | false
        info     | null    | false
        info     | error   | true
        info     | warning | true
        info     | info    | true
        info     | debug   | false
        info     | trace   | false
        debug    | null    | false
        debug    | error   | true
        debug    | warning | true
        debug    | info    | true
        debug    | debug   | true
        debug    | trace   | false
        trace    | null    | false
        trace    | error   | true
        trace    | warning | true
        trace    | info    | true
        trace    | debug   | true
        trace    | trace   | true
    }
}
