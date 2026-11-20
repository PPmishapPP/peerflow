package ru.peerflow.demo.data

import androidx.compose.ui.graphics.Color
import ru.peerflow.demo.ui.theme.BudgetAccent
import ru.peerflow.demo.ui.theme.HeatHot
import ru.peerflow.demo.ui.theme.HeatWarm
import ru.peerflow.demo.ui.theme.LegitAccent
import ru.peerflow.demo.ui.theme.VoteDown
import ru.peerflow.demo.ui.theme.VoteUp

/**
 * Демонстрационные данные прототипа. Бэкенда нет: всё живёт в памяти,
 * чтобы показать механику PeerFlow на примере сообщества «Маяк».
 *
 * Главный сюжет:
 *  - реальное дело «Аренда помещения для лекции» уже в работе: выбран лидер,
 *    он отчитывается, и любой может забрать свою легитимность;
 *  - шумное «Борьба с 5G-вышками» набрало много сообщений, но заминусовано
 *    и оседает внизу среди тех, что ещё только обсуждаются.
 */

enum class SortTab { POPULAR, BUDGET, NEW }

enum class MessageFilter { POPULAR, NEW }

/** На какой стадии находится предложение. */
enum class ProposalStage(val label: String) {
    IN_WORK("В работе"),
    DISCUSSION("На обсуждении"),
}

enum class Reaction(val label: String, val symbol: String, val color: Color) {
    PLUS("Согласен и по делу", "+", VoteUp),
    MINUS("Не согласен, но по делу", "−", VoteDown),
    SPAM("Это спам / не по делу", "⚑", Color(0xFF78716C)),
}

data class CommunityMember(
    val id: String,
    val name: String,
    val reputation: Int,
    val joined: String,
    val avatarColor: Color,
) {
    val initials: String
        get() = name.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")
}

data class Candidate(
    val member: CommunityMember,
    val legitimacy: Int,
)

data class DiscussionMessage(
    val id: String,
    val author: CommunityMember,
    val text: String,
    val plus: Int,
    val minus: Int,
    val spam: Int,
    val agoLabel: String,
    val isReply: Boolean = false,
    val replies: List<DiscussionMessage> = emptyList(),
) {
    val isHidden: Boolean get() = spam >= 3
}

/** Отчёт лидера, прикреплённый к задаче в работе. */
data class LeaderReport(
    val text: String,
    val attachment: String?,
    val commentAuthor: CommunityMember,
    val commentText: String,
    val commentPlus: Int,
)

data class Proposal(
    val id: String,
    val title: String,
    val summary: String,
    val author: CommunityMember,
    val budget: Int,
    val comments: Int,
    val participants: Int,
    val forVotes: Int,
    val againstVotes: Int,
    val stage: ProposalStage,
    val heat: String,
    val heatColor: Color,
    // Поля стадии «в работе».
    val leader: CommunityMember? = null,
    val leaderLegitimacy: Int = 0,
    val leaderLegitimacyFrom: Int = 0,
    /** Передал ли текущий пользователь свою легитимность лидеру этой задачи. */
    val myLegitimacyDelegated: Boolean = false,
    val report: LeaderReport? = null,
    // Поля стадии обсуждения.
    val candidates: List<Candidate> = emptyList(),
    val messages: List<DiscussionMessage> = emptyList(),
    /** Только что внесено в этом сеансе — чтобы карточка отличалась от демо-данных. */
    val justCreated: Boolean = false,
) {
    /**
     * Метрика популярности для главного экрана.
     *
     * Документ описывал её как «комментарии + уникальные участники», но на
     * демо-данных сразу видно изъян: зашумлённое предложение про 5G набрало
     * 412 сообщений и вылезало на первое место — метрика награждала спам.
     * Поэтому популярность считается как живая дискуссия С поддержкой:
     * уникальные участники + перевес голосов (за − против).
     */
    val popularity: Int get() = participants + (forVotes - againstVotes)

    val legitimacyPercent: Int
        get() = if (leaderLegitimacyFrom == 0) 0 else leaderLegitimacy * 100 / leaderLegitimacyFrom
}

object DemoData {
    val anna = CommunityMember("m1", "Анна Ковалёва", 42, "01.02.2024", LegitAccent)
    val boris = CommunityMember("m2", "Борис Титов", 17, "12.03.2024", BudgetAccent)
    val vera = CommunityMember("m3", "Вера Соколова", 88, "05.01.2024", HeatWarm)
    val grigory = CommunityMember("m4", "Григорий Панов", 5, "21.06.2024", VoteUp)
    val dina = CommunityMember("m5", "Дина Ершова", 31, "30.09.2024", HeatHot)
    val lev = CommunityMember("m6", "Лев Гордеев", 12, "14.11.2024", VoteDown)

    val members = listOf(anna, boris, vera, grigory, dina, lev)

    val currentUser = anna

    // ---- Задача в работе №1: аренда помещения. Лидер — Вера. ----
    val rentProposal = Proposal(
        id = "p1",
        title = "Аренда помещения для лекции",
        summary = "Снять зал на 40 человек на субботу для лекции о локальной " +
            "самоорганизации. Нужны проектор и чайник.",
        author = dina,
        budget = 12000,
        comments = 34,
        participants = 9,
        forVotes = 27,
        againstVotes = 4,
        stage = ProposalStage.IN_WORK,
        heat = "Идёт исполнение",
        heatColor = HeatHot,
        leader = vera,
        leaderLegitimacy = 21,
        leaderLegitimacyFrom = 30,
        myLegitimacyDelegated = true,
        report = LeaderReport(
            text = "Зал оплачен, предоплата 6 000 ₽ внесена. Проектор завезли сегодня " +
                "утром, проверили — работает. Осталось докупить воду и стаканы.",
            attachment = "chek-oplata-zala.pdf · квитанция 6 000 ₽",
            commentAuthor = boris,
            commentText = "Квитанцию вижу, вопросов нет. Жду финальный список покупок.",
            commentPlus = 6,
        ),
        messages = listOf(
            DiscussionMessage(
                id = "r1",
                author = dina,
                text = "Нашла зал у метро: 12 000 за субботу, есть проектор. " +
                    "Предоплату нужно внести до четверга.",
                plus = 18,
                minus = 1,
                spam = 0,
                agoLabel = "2 ч назад",
                replies = listOf(
                    DiscussionMessage(
                        id = "r1a",
                        author = lev,
                        text = "Дорого. В соседнем доме культуры на 3 000 дешевле, " +
                            "но проектор надо везти свой.",
                        plus = 9,
                        minus = 2,
                        spam = 0,
                        agoLabel = "1 ч назад",
                        isReply = true,
                    ),
                    DiscussionMessage(
                        id = "r1b",
                        author = anna,
                        text = "Свой проектор везти — это ещё час на сборку. " +
                            "Предлагаю голосовать за вариант у метро.",
                        plus = 12,
                        minus = 0,
                        spam = 0,
                        agoLabel = "40 мин назад",
                        isReply = true,
                    ),
                ),
            ),
            DiscussionMessage(
                id = "r2",
                author = grigory,
                text = "Могу помочь с перевозкой проектора на своей машине, если выберем ДК.",
                plus = 6,
                minus = 0,
                spam = 0,
                agoLabel = "1 ч назад",
            ),
            DiscussionMessage(
                id = "r3",
                author = lev,
                text = "Скидывайтесь все по 500, иначе опять я один оплачу.",
                plus = 3,
                minus = 11,
                spam = 1,
                agoLabel = "50 мин назад",
            ),
            DiscussionMessage(
                id = "r4",
                author = boris,
                text = "РЕКЛАМА: сдам гараж, недорого!!! звоните!!!",
                plus = 0,
                minus = 2,
                spam = 6,
                agoLabel = "30 мин назад",
            ),
        ),
    )

    // ---- Задача в работе №2: дверь. Лидер — Григорий. ----
    val repairProposal = Proposal(
        id = "p3",
        title = "Починить входную дверь в клубную комнату",
        summary = "Дверь не закрывается, зимой дует. Нужен мастер и новые петли.",
        author = grigory,
        budget = 4500,
        comments = 11,
        participants = 5,
        forVotes = 14,
        againstVotes = 1,
        stage = ProposalStage.IN_WORK,
        heat = "Идёт исполнение",
        heatColor = HeatWarm,
        leader = grigory,
        leaderLegitimacy = 12,
        leaderLegitimacyFrom = 16,
        myLegitimacyDelegated = true,
        report = LeaderReport(
            text = "Мастер приехал в четверг, заменил петли и замок. Дверь закрывается " +
                "плотно, дуть больше не будет. Работы приняты.",
            attachment = "chek-master.pdf · квитанция 4 500 ₽",
            commentAuthor = boris,
            commentText = "Проверил сам — закрывается отлично. Спасибо!",
            commentPlus = 4,
        ),
        messages = listOf(
            DiscussionMessage(
                id = "d1",
                author = grigory,
                text = "Мастер взялся за 4 500, петли и замок включены. Делает в четверг.",
                plus = 7,
                minus = 0,
                spam = 0,
                agoLabel = "вчера",
            ),
        ),
    )

    // ---- Обсуждение №1: вода и посуда. ----
    val waterProposal = Proposal(
        id = "p4",
        title = "Закупить воду и посуду для мероприятий",
        summary = "Одноразовая посуда, чай, сахар и вода на ближайшие два месяца.",
        author = boris,
        budget = 2000,
        comments = 6,
        participants = 4,
        forVotes = 11,
        againstVotes = 0,
        stage = ProposalStage.DISCUSSION,
        heat = "Спокойно",
        heatColor = VoteUp,
        candidates = listOf(
            Candidate(boris, 4),
            Candidate(dina, 2),
        ),
        messages = listOf(
            DiscussionMessage(
                id = "w1",
                author = boris,
                text = "Возьму на себя закупку, чеки приложу в отчёт.",
                plus = 5,
                minus = 0,
                spam = 0,
                agoLabel = "2 дня назад",
            ),
        ),
    )

    // ---- Обсуждение №2: шумное предложение про 5G. ----
    val fiveGProposal = Proposal(
        id = "p2",
        title = "Борьба с вышками 5G",
        summary = "Собрать подписи и жалобы против установки вышек 5G рядом " +
            "с жилыми домами. Прогнать через все инстанции.",
        author = lev,
        budget = 3000,
        comments = 412,
        participants = 12,
        forVotes = 9,
        againstVotes = 24,
        stage = ProposalStage.DISCUSSION,
        heat = "Много сообщений",
        heatColor = HeatWarm,
        candidates = listOf(
            Candidate(lev, 5),
            Candidate(grigory, 3),
        ),
        messages = listOf(
            DiscussionMessage(
                id = "g1",
                author = lev,
                text = "Вышки убивают! Надо срочно писать во все инстанции, вот ссылка на петицию.",
                plus = 4,
                minus = 15,
                spam = 2,
                agoLabel = "3 ч назад",
            ),
            DiscussionMessage(
                id = "g2",
                author = grigory,
                text = "Опять двадцать пять. Мы вообще про лекцию договаривались.",
                plus = 2,
                minus = 1,
                spam = 0,
                agoLabel = "2 ч назад",
            ),
            DiscussionMessage(
                id = "g3",
                author = lev,
                text = "5G — это чипирование, читайте телеграм-каналы!!111",
                plus = 1,
                minus = 4,
                spam = 8,
                agoLabel = "2 ч назад",
            ),
        ),
    )

    val allProposals = listOf(rentProposal, repairProposal, waterProposal, fiveGProposal)

    /** Предложения, внесённые во время демонстрации: показываются рядом с демо-данными. */
    private val created = mutableListOf<Proposal>()

    private fun current(): List<Proposal> = created + allProposals

    fun addProposal(proposal: Proposal) {
        created.add(0, proposal)
    }

    fun nextCreatedId(): Int = created.size + 1

    fun sorted(tab: SortTab): List<Proposal> = when (tab) {
        SortTab.POPULAR -> current().sortedByDescending { it.popularity }
        SortTab.BUDGET -> current().sortedByDescending { it.budget }
        SortTab.NEW -> current().reversed()
    }

    fun byId(id: String): Proposal = current().first { it.id == id }

    // ---- Мок «боль»: тот же разговор, но в обычном мессенджере ----
    data class ChatLine(val author: String, val text: String, val self: Boolean = false)

    val chaoticChat = listOf(
        ChatLine("Лев", "Вышки 5G убивают! Скидываю петицию, подписываем все!"),
        ChatLine("Вера", "Лев, откуда данные? Пока вижу только ссылку на телеграм-канал."),
        ChatLine("Лев", "5G — это чипирование, читайте телеграм-каналы!!111"),
        ChatLine("Григорий", "У меня на работе вышка стоит третий год, никто не чипировался."),
        ChatLine("Лев", "Россия в опасности, просыпаемся!"),
        ChatLine("Дина", "Давайте ссылку на исследование, а не на канал. Готова почитать."),
        ChatLine("Лев", "Вот статья, вот видео, вот ещё канал"),
        ChatLine("Борис", "Эта «статья» с сайта, который продаёт защиту от излучения."),
        ChatLine("Лев", "5G! 5G! 5G! репостим всем!!!"),
        ChatLine("Вера", "Ты уже третий раз одно и то же скидываешь."),
        ChatLine("Григорий", "……"),
        ChatLine("Лев", "Почему все молчат? Это заговор!"),
        ChatLine("Лев", "Ссылка на петицию №2"),
        ChatLine("Дина", "Мне кажется, мы уже по кругу ходим 😔"),
        ChatLine("Лев", "Кто не с нами, тот против нас"),
    )
}
