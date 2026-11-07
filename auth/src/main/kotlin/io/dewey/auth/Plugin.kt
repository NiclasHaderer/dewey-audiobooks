package io.dewey.auth

import io.ktor.server.application.ApplicationPlugin
import io.ktor.server.application.createApplicationPlugin

object DeweyAuthenticationPlugin {
    fun <PERMISSIONS, UPDATE_PERMISSIONS, TOKEN_PERMISSIONS> build():
        ApplicationPlugin<DeweyAuthConfigBuilder<PERMISSIONS, UPDATE_PERMISSIONS, TOKEN_PERMISSIONS>> =
        createApplicationPlugin(name = "DeweyAuthPlugin", createConfiguration = ::DeweyAuthConfigBuilder) {
            val pluginConfig = pluginConfig.build()
            pluginConfig.run { application.applyGuards() }

            onCall { call -> call.attributes.put(PLUGIN_CONFIG_KEY, pluginConfig) }
        }
}
