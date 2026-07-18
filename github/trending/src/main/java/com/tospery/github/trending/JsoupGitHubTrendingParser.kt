package com.tospery.github.trending

import com.tospery.github.model.core.Repo
import com.tospery.github.model.core.RepoId
import com.tospery.github.model.core.User
import com.tospery.github.model.core.UserId
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * 基于 jsoup 的 GitHub Trending HTML 解析器。
 *
 * GitHub Trending 是网页而不是稳定 API，所以选择较宽松的选择器，降低页面微调导致解析失败的概率。
 */
class JsoupGitHubTrendingParser : GitHubTrendingParser {
    override fun parseRepositories(html: String): List<Repo> {
        val document = Jsoup.parse(html, GITHUB_BASE_URL)
        return document.select("article.Box-row").mapNotNull { article ->
            article.parseRepository()
        }
    }

    override fun parseDevelopers(html: String): List<User> {
        val document = Jsoup.parse(html, GITHUB_BASE_URL)
        return document.select("article.Box-row").mapNotNull { article ->
            article.parseDeveloper()
        }
    }

    private fun Element.parseRepository(): Repo? {
        val repoLink = selectFirst("h2 a") ?: return null
        val path = repoLink.attr("href").trim('/')
        val parts = path.split("/")
        if (parts.size < 2) return null

        val owner = parts[0].trim()
        val name = parts[1].trim()
        if (owner.isBlank() || name.isBlank()) return null

        val description = selectFirst("p")?.text()?.takeIf { it.isNotBlank() }
        val languageElement = selectFirst("[itemprop=programmingLanguage]")
        val language = languageElement?.text()?.takeIf { it.isNotBlank() }
        val stars = select("a[href=/$owner/$name/stargazers]").firstOrNull()?.text()?.toCompactIntOrNull()
        val forks = select("a[href=/$owner/$name/forks]").firstOrNull()?.text()?.toCompactIntOrNull()

        return Repo(
            id = RepoId("trending:$owner/$name"),
            githubId = null,
            ownerLogin = owner,
            name = name,
            fullName = "$owner/$name",
            description = description,
            htmlUrl = "$GITHUB_BASE_URL/$owner/$name",
            language = language,
            stargazersCount = stars,
            forksCount = forks,
            watchersCount = null,
            openIssuesCount = null,
            licenseName = null,
            isPrivate = null,
            isFork = null,
            updatedAt = null,
        )
    }

    private fun Element.parseDeveloper(): User? {
        val userLink = selectFirst("h1 a, h2 a") ?: return null
        val username = userLink.attr("href").trim('/').takeIf { it.isNotBlank() } ?: return null
        val displayName = userLink.text().trim().takeIf { it.isNotBlank() }
        val avatarUrl = selectFirst("img")?.absUrl("src")?.takeIf { it.isNotBlank() }

        return User(
            id = UserId("trending:$username"),
            githubId = null,
            login = username,
            name = displayName,
            avatarUrl = avatarUrl,
            htmlUrl = "$GITHUB_BASE_URL/$username",
            bio = null,
            company = null,
            location = null,
            blog = null,
            followersCount = null,
            followingCount = null,
        )
    }

    private fun String.toCompactIntOrNull(): Int? {
        val normalized = trim()
            .replace(",", "")
            .lowercase()

        val numberText = normalized
            .substringBefore("stars today")
            .substringBefore("star today")
            .trim()

        return when {
            numberText.endsWith("k") -> {
                val value = numberText.removeSuffix("k").toDoubleOrNull() ?: return null
                (value * 1_000).toInt()
            }
            numberText.endsWith("m") -> {
                val value = numberText.removeSuffix("m").toDoubleOrNull() ?: return null
                (value * 1_000_000).toInt()
            }
            else -> numberText.toIntOrNull()
        }
    }

    private companion object {
        const val GITHUB_BASE_URL = "https://github.com"
    }
}
