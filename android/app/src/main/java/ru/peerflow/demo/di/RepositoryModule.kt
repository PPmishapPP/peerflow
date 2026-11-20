package ru.peerflow.demo.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.peerflow.demo.data.repo.AccountRepository
import ru.peerflow.demo.data.repo.ApiAccountRepository
import ru.peerflow.demo.data.repo.ApiDiscussionRepository
import ru.peerflow.demo.data.repo.ApiJoinRequestRepository
import ru.peerflow.demo.data.repo.ApiLegitimacyRepository
import ru.peerflow.demo.data.repo.ApiProposalRepository
import ru.peerflow.demo.data.repo.ApiReportRepository
import ru.peerflow.demo.data.repo.DiscussionRepository
import ru.peerflow.demo.data.repo.JoinRequestRepository
import ru.peerflow.demo.data.repo.LegitimacyRepository
import ru.peerflow.demo.data.repo.ProposalRepository
import ru.peerflow.demo.data.repo.ReportRepository
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

    @Binds
    @Singleton
    abstract fun proposalRepository(impl: ApiProposalRepository): ProposalRepository

    @Binds
    @Singleton
    abstract fun discussionRepository(impl: ApiDiscussionRepository): DiscussionRepository

    @Binds
    @Singleton
    abstract fun legitimacyRepository(impl: ApiLegitimacyRepository): LegitimacyRepository

    @Binds
    @Singleton
    abstract fun reportRepository(impl: ApiReportRepository): ReportRepository
}
