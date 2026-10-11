package com.xen.xenmind.data

import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<Msg>,
    val stream: Boolean = false,
    val temperature: Double = 0.3
)

@Serializable
data class Msg(val role: String, val content: String)

@Serializable
data class ChatResponse(val choices: List<Choice> = emptyList())

@Serializable
data class Choice(val message: Msg)
