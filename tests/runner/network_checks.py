"""Recognize runtime failures independently of whether test JSON was written."""
from pathlib import Path
import re

NETWORK_FAILURE = re.compile(
    r"Failed to serialize data attachment contact:mailbox_data|"
    r"(?:EncoderException|DecoderException): Failed to (?:encode|decode) packet|"
    r"Failed (?:encoding|decoding) custom payload|"
    r"Received quest sync data for an unrecognized (?:task|reward) type|"
    r"(?:lost connection|disconnected(?: with reason)?): Internal Exception|"
    r"Attempted to load class .+ for invalid dist DEDICATED_SERVER|"
    r"(?:NoClassDefFoundError|ClassNotFoundException): (?:net[./]minecraft[./]client|com[./]flechazo[./]contact[./]client)|"
    r"(?:NoClassDefFoundError|ClassNotFoundException): dev[./]latvian[./]mods[./](?:kubejs|rhino)|"
    r"(?:NoClassDefFoundError|ClassNotFoundException): mezz[./]jei[./]|"
    r"(?:MixinApplyError|InvalidMixinException):|"
    r"Crash during font initialization|Failed to initialize the mod loading system and display|"
    r"An error occurred initializing a font for rendering|java\.nio\.file\.FileSystemNotFoundException:|"
    r"java\.lang\.(?:ExceptionInInitializerError|reflect\.InaccessibleObjectException):|Game crashed! Crash report saved"
    r"|FAILED TO BIND TO PORT|Encountered an unexpected exception|Exception stopping the server"
)


def inspect_log(path):
    path = Path(path)
    if not path.is_file():
        return ["Runtime log is missing"]
    return [line.strip() for line in path.read_text(encoding="utf-8", errors="replace").splitlines()
            if NETWORK_FAILURE.search(line)]
