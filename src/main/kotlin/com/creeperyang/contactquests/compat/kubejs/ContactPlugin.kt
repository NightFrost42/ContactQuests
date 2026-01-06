package com.creeperyang.contactquests.compat.kubejs

import dev.latvian.mods.kubejs.event.EventGroupRegistry
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin
import dev.latvian.mods.kubejs.script.BindingRegistry
import org.apache.logging.log4j.LogManager

class ContactPlugin : KubeJSPlugin {

    private val logger = LogManager.getLogger("contactquests-kubejs-plugin")

    override fun init() {
        logger.info("ContactQuests KubeJS Plugin Initialized via kubejs.plugins.txt!")
    }

    override fun registerEvents(registry: EventGroupRegistry) {
        registry.register(ContactKubeJSPlugin.GROUP)
        logger.info("Registered EventGroup: ${ContactKubeJSPlugin.GROUP.name}")
    }

    override fun registerBindings(bindings: BindingRegistry) {
        bindings.add("ContactQuests", ContactKubeJSPlugin)

        logger.info("Manually bound 'ContactQuests' to script scope")
    }
}