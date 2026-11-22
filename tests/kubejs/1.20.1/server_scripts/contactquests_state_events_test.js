(function () {
  var Fixtures = Java.loadClass('com.creeperyang.contactquests.test.ContactQuestsNewFeatureFixtures')
  function record(event) {
    Fixtures.recordScriptEvent(JSON.stringify({
      kind:String(event.kind), questId:String(event.questId), tag:String(event.tag),
      teamId:String(event.teamId), playerId:event.playerId == null ? '' : String(event.playerId),
      cause:String(event.cause), sequence:String(event.sequence),
      mutexBefore:Boolean(event.before.mutex), mutexAfter:Boolean(event.after.mutex),
      blockBefore:Boolean(event.before.scriptBlocked), blockAfter:Boolean(event.after.scriptBlocked)
    }))
  }
  ContactEvents.tagUnlocked(event => { try { record(event) } catch (error) { console.error('[cq_event_test] ' + error) } })
  ContactEvents.mutexChanged(event => { try { record(event) } catch (error) { console.error('[cq_event_test] ' + error) } })
  ContactEvents.scriptBlockChanged(event => { try { record(event) } catch (error) { console.error('[cq_event_test] ' + error) } })
})()
