package com.tospery.github.trending

import com.tospery.github.model.core.Repo
import com.tospery.github.model.core.RepoId
import com.tospery.github.model.core.User
import com.tospery.github.model.core.UserId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsoupGitHubTrendingParserTest {
    private val parser = JsoupGitHubTrendingParser()

    @Test
    fun parseRepositoriesMapsRepositoryFields() {
        val html = """
            <html>
              <body>
                <article class="Box-row">
                  <h2>
                    <a href="/owner/repo">
                      owner / repo
                    </a>
                  </h2>
                  <p>Repository description</p>
                  <span style="background-color: #F18E33;"></span>
                  <span itemprop="programmingLanguage">Kotlin</span>
                  <a href="/owner/repo/stargazers">1,234</a>
                  <a href="/owner/repo/forks">56</a>
                  <span class="float-sm-right">78 stars today</span>
                </article>
              </body>
            </html>
        """.trimIndent()

        val repositories = parser.parseRepositories(html)

        assertEquals(
            listOf(
                Repo(
                    id = RepoId("trending:owner/repo"),
                    githubId = null,
                    ownerLogin = "owner",
                    name = "repo",
                    fullName = "owner/repo",
                    description = "Repository description",
                    htmlUrl = "https://github.com/owner/repo",
                    language = "Kotlin",
                    stargazersCount = 1234,
                    forksCount = 56,
                    watchersCount = null,
                    openIssuesCount = null,
                    licenseName = null,
                    isPrivate = null,
                    isFork = null,
                    updatedAt = null,
                ),
            ),
            repositories,
        )
    }

    @Test
    fun parseRepositoriesSkipsRowsWithoutRepositoryPath() {
        val html = """
            <html>
              <body>
                <article class="Box-row">
                  <h2><a href="/only-owner">invalid</a></h2>
                </article>
              </body>
            </html>
        """.trimIndent()

        assertTrue(parser.parseRepositories(html).isEmpty())
    }

    @Test
    fun parseDevelopersMapsDeveloperFields() {
        val html = """
        <html>
          <body>
            <article class="Box-row">
              <img src="/avatars/u/1?v=4" />
              <h1>
                <a href="/octocat">Octocat</a>
              </h1>
              <article>
                <a href="/octocat/hello-world">hello-world</a>
                <p>Demo repository</p>
              </article>
            </article>
          </body>
        </html>
    """.trimIndent()

        val developers = parser.parseDevelopers(html)

        assertEquals(
            listOf(
                User(
                    id = UserId("trending:octocat"),
                    githubId = null,
                    login = "octocat",
                    name = "Octocat",
                    avatarUrl = "https://github.com/avatars/u/1?v=4",
                    htmlUrl = "https://github.com/octocat",
                    bio = null,
                    company = null,
                    location = null,
                    blog = null,
                    followersCount = null,
                    followingCount = null,
                ),
            ),
            developers,
        )
    }
}
