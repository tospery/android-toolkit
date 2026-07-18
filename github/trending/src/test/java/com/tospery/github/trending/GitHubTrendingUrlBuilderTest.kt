package com.tospery.github.trending

import com.tospery.github.model.core.ProgrammingLanguage
import com.tospery.github.model.core.SpokenLanguage
import com.tospery.github.model.core.DateRange
import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubTrendingUrlBuilderTest {
    private val builder = GitHubTrendingUrlBuilder()

    @Test
    fun repositoriesUrlBuildsDefaultTrendingUrl() {
        val url = builder.repositoriesUrl()

        assertEquals(
            "https://github.com/trending?since=daily",
            url,
        )
    }

    @Test
    fun repositoriesUrlBuildsUrlWithLanguageSpokenLanguageAndDateRange() {
        val url = builder.repositoriesUrl(
            programmingLanguage = ProgrammingLanguage(id = "Kotlin", name = "Kotlin"),
            spokenLanguage = SpokenLanguage(id = "zh", name = "Chinese"),
            dateRange = DateRange.MONTHLY,
        )

        assertEquals(
            "https://github.com/trending/Kotlin?since=monthly&spoken_language_code=zh",
            url,
        )
    }

    @Test
    fun developersUrlBuildsUrlWithLanguageAndDateRange() {
        val url = builder.developersUrl(
            programmingLanguage = ProgrammingLanguage(id = "Kotlin", name = "Kotlin"),
            dateRange = DateRange.WEEKLY,
        )

        assertEquals(
            "https://github.com/trending/developers/Kotlin?since=weekly",
            url,
        )
    }

    @Test
    fun urlBuilderEncodesSpecialCharacters() {
        val url = builder.repositoriesUrl(
            programmingLanguage = ProgrammingLanguage(id = "C++", name = "C++"),
        )

        assertEquals(
            "https://github.com/trending/C%2B%2B?since=daily",
            url,
        )
    }
}