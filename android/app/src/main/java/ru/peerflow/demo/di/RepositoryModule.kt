package ru.peerflow.demo.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.peerflow.demo.data.repo.AccountRepository
import ru.peerflow.demo.data.repo.ApiAccountRepository
import ru.peerflow.demo.data.repo.ApiJoinRequestRepository
import ru.peerflow.demo.data.repo.JoinRequestRepository
import javax.inject.Singleton

/**
 * Привязка реализаций репозиториев к интерфейсам.
 *
 * ViewModel зависит от интерфейса, поэтому тест подставляет фейк
 * конструктором и не поднимает Hilt.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun accountRepository(impl: ApiAccountRepository): AccountRepository

    @Binds
    @Singleton
    abstract fun joinRequestRepository(impl: ApiJoinRequestRepository): JoinRequestRepository
}
