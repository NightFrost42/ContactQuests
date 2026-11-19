// Script execution is recorded independently of the Java GUI assertions.
var CQClientTests = Java.loadClass('com.creeperyang.contactquests.test.ContactQuestsClientTestController')
var seenScreens = []
ClientEvents.tick(event => {
  var name = String(CQClientTests.getScreenName())
  CQClientTests.recordScriptTick()
  if (seenScreens.length === 0 || seenScreens[seenScreens.length - 1] !== name) {
    seenScreens.push(name)
    console.info(`[contactquests_test] CLIENT_SCREEN ${name}`)
  }
})
