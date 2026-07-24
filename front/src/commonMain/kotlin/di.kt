import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.instance

/**
 * Constructor-style DI (per `front/ARCHITECTURE.md §6`): one singleton per
 * layer, wired explicitly rather than via reflection/annotation scanning.
 * Provided to the composable tree once, in [App], via `withDI`.
 */
val appDI = DI {
    bindSingleton<TokenStore> { createTokenStore() }
    bindSingleton { Session() }
    bindSingleton { createHttpClient(instance(), instance()) }
    bindSingleton { AuthRepository(instance(), instance(), instance()) }
    bindSingleton { HousesRepository(instance()) }
    bindSingleton { EventsRepository(instance()) }
    bindSingleton { UsersRepository(instance()) }
    bindSingleton { LeaderboardConfig() }
    bindSingleton { HistoryFilter() }
}
