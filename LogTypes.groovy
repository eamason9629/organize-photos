import groovy.transform.CompileStatic

@CompileStatic
enum LogTypes {
    trace,
    debug,
    info,
    warning,
    error

    boolean shouldLog(LogTypes logLevel) {
        return null != logLevel && (compareTo(logLevel) <= 0 || info == logLevel)
    }
}