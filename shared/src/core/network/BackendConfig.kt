package core.network

/** Base URL of the indoor backend. Override at app start (e.g. Android emulator: http://10.0.2.2:8080). */
object BackendConfig {
    var baseUrl: String = "http://localhost:8080"
}
