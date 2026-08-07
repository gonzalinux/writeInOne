package com.gonzalinux.config

import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.DataHolder
import com.vladsch.flexmark.util.data.MutableDataSet
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MarkdownConfig {

    @Bean
    fun markdownOptions(): DataHolder =
        MutableDataSet().set(Parser.EXTENSIONS, listOf(TablesExtension.create()))

    @Bean
    fun markdownParser(markdownOptions: DataHolder): Parser =
        Parser.builder(markdownOptions).build()

    @Bean
    fun markdownRenderer(markdownOptions: DataHolder): HtmlRenderer =
        HtmlRenderer.builder(markdownOptions).build()
}
