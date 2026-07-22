import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.js.Js

actual val httpClientEngineFactory: HttpClientEngineFactory<*> = Js
