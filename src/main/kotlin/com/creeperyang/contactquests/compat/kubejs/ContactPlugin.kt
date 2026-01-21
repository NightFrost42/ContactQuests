package com.creeperyang.contactquests.compat.kubejs

import dev.latvian.mods.kubejs.KubeJSPlugin
import dev.latvian.mods.kubejs.script.BindingsEvent
import org.apache.logging.log4j.LogManager

class ContactPlugin : KubeJSPlugin() {

    private val logger = LogManager.getLogger("contactquests-kubejs-plugin")

    override fun init() {
        logger.info("ContactQuests KubeJS Plugin Initialized!")
        ContactKubeJSPlugin.init()
    }

    override fun registerEvents() {
        ContactKubeJSPlugin.GROUP.register()
        logger.info("Registered EventGroup: ${ContactKubeJSPlugin.GROUP.name}")
    }

    override fun registerBindings(event: BindingsEvent) {
        event.add("ContactQuests", ContactKubeJSPlugin)
        logger.info("Manually bound 'ContactQuests' to script scope")
    }
}