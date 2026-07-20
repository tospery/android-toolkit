package com.tospery.nav

import com.tospery.base.logging.LogAttribute
import com.tospery.base.logging.LogTags
import com.tospery.base.logging.info
import com.tospery.buildmetadata.module_nav.ModuleMetadata

internal val NAV_LOG_TAG: String =
    LogTags.child(
        parent = LogTags.moduleTag(ModuleMetadata.path),
        segment = "navigation",
    )

/**
 * 输出即将交给平台导航实现的 route URL。
 *
 * 查询参数默认仅保留名称，避免 OAuth code、token、任意 Web URL 或弹层文案进入日志。
 * 调用方只能为已确认不含敏感数据的参数显式开放 value。
 */
fun logRouteNavigation(
    routeUrl: String,
    source: String? = null,
    visibleQueryParameters: Set<String> = emptySet(),
) {
    val attributes =
        buildList {
            add(
                LogAttribute(
                    key = "route_url",
                    value = routeUrl.redactNavigationUrl(visibleQueryParameters),
                ),
            )
            source?.takeIf(String::isNotBlank)?.let { value ->
                add(LogAttribute(key = "source", value = value))
            }
        }

    info(
        tag = NAV_LOG_TAG,
        attributes = attributes,
    ) {
        "使用路由 URL 执行页面导航。"
    }
}

/**
 * 生成适合日志输出的 URL。fragment 与未显式开放的 query value 会被隐藏。
 */
fun String.redactNavigationUrl(
    visibleQueryParameters: Set<String> = emptySet(),
): String {
    val trimmed = trim()
    val fragmentIndex = trimmed.indexOf('#')
    val withoutFragment =
        if (fragmentIndex >= 0) trimmed.substring(0, fragmentIndex) else trimmed
    val fragmentSuffix = if (fragmentIndex >= 0) "#<redacted>" else ""
    val queryIndex = withoutFragment.indexOf('?')

    if (queryIndex < 0) return withoutFragment + fragmentSuffix

    val path = withoutFragment.substring(0, queryIndex)
    val rawQuery = withoutFragment.substring(queryIndex + 1)
    if (rawQuery.isEmpty()) return "$path?$fragmentSuffix"

    val safeQuery =
        rawQuery
            .split('&')
            .joinToString(separator = "&") { parameter ->
                parameter.redactQueryValue(visibleQueryParameters)
            }

    return "$path?$safeQuery$fragmentSuffix"
}

fun UrlNavigationTarget.toNavigationLogUrl(): String =
    when (this) {
        is UrlNavigationTarget.InternalRoute -> route.value.redactNavigationUrl()
        is UrlNavigationTarget.ExternalApp -> uri.redactOpaqueNavigationUri()
        is UrlNavigationTarget.SystemUri -> uri.redactOpaqueNavigationUri()
        is UrlNavigationTarget.WebUrl -> url.redactNavigationUrl()
        is UrlNavigationTarget.Unknown -> uri.redactNavigationUrl()
    }

internal fun UrlNavigationTarget.navigationLogType(): String =
    javaClass.simpleName

internal fun NavAction.navigationLogType(): String =
    javaClass.simpleName

private fun String.redactQueryValue(
    visibleQueryParameters: Set<String>,
): String {
    val separatorIndex = indexOf('=')
    if (separatorIndex < 0) return this

    val key = substring(0, separatorIndex)
    return if (key in visibleQueryParameters) {
        this
    } else {
        "$key=<redacted>"
    }
}

private fun String.redactOpaqueNavigationUri(): String {
    val scheme = substringBefore(':').takeIf(String::isNotBlank)
    return if (scheme == null || ':' !in this) {
        redactNavigationUrl()
    } else {
        "$scheme:<redacted>"
    }
}
