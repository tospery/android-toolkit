package com.tospery.github.trending

import com.tospery.github.model.core.Repo
import com.tospery.github.model.core.User

/**
 * GitHub Trending HTML 解析器。
 *
 * 语言筛选项使用固定数据返回；解析器只负责从页面 HTML 中解析列表内容。
 */
interface GitHubTrendingParser {
    fun parseRepositories(html: String): List<Repo>

    fun parseDevelopers(html: String): List<User>
}
