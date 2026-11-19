// Fixed KubeJS fixture for the ContactQuests integration test suite.
// It intentionally creates NPCs before any FTB task is registered so the
// no-task NPC receive path is exercised by the Contact client scenario.

(function() {
var TestResults = Java.loadClass('com.creeperyang.contactquests.test.ContactQuestsTestServerController')
var TestAssertions = Java.loadClass('com.creeperyang.contactquests.test.ContactQuestsScriptAssertions')
var ReadOnlyRejoin = Java.loadClass('java.lang.Boolean').getBoolean('contactquests.test.rejoin')

var TEST_NPCS = {
  noTaskImmediate: 'CQTest_NoTask_Immediate',
  noTaskDelayed: 'CQTest_NoTask_Delayed',
  aliasCandidate: 'CQTest_AliasCandidate'
}

var state = {
  loaded: false,
  mailEvents: 0,
  lastRecipient: '',
  failures: [],
  apiExerciseDone: false,
  configClearExerciseDone: false,
  graphEditDone: false,
  stateControlsDone: false,
  deliveryDone: false,
  interceptedTypes: {}
}

function fail(message) {
  state.failures.push(message)
  TestResults.recordFailure(String(message))
  console.error(`[contactquests_test] FAIL ${message}`)
}

function pass(message) {
  console.info(`[contactquests_test] PASS ${message}`)
}

ServerEvents.loaded(event => {
  TestResults.reset()
  if (ReadOnlyRejoin) {
    // This phase must observe disk state from a different Java process. Never
    // rewrite the fixture values here or a broken load would be concealed.
    TestResults.markLoaded()
    return
  }
  var level = event.server.overworld()
  state.level = level

  if (typeof ContactQuests === 'undefined') {
    fail('ContactQuests KubeJS binding is missing')
    return
  }

  ContactQuests.setNpcConfig(level, TEST_NPCS.noTaskImmediate, 0, false)
  ContactQuests.setNpcConfig(level, TEST_NPCS.noTaskDelayed, 40, false)
  // This is the no-task NPC used through the real Contact postbox UI. It is
  // visible to Contact's recipient lookup while still having no FTB task.
  ContactQuests.setNpcConfig(level, TEST_NPCS.aliasCandidate, 40, true)
  TestResults.recordCase('kubejs.npc.no_task.immediate',
    TestAssertions.npcConfig(level, TEST_NPCS.noTaskImmediate, 0, false), 'Configured zero delay before task registration')
  TestResults.recordCase('kubejs.npc.no_task.delayed',
    TestAssertions.npcConfig(level, TEST_NPCS.noTaskDelayed, 40, false), 'Configured delay=40 and autoFill=false')
  TestResults.recordCase('kubejs.npc.no_task.auto_fill',
    TestAssertions.npcConfig(level, TEST_NPCS.aliasCandidate, 40, true), 'Configured delay=40 and autoFill=true')

  try {
    if (!ContactQuests.createPostcard(
      'ContactQuestsTest',
      'fixed test postcard',
      ['ContactQuestsTestTag'],
      'contact:default',
      false
    ) || ContactQuests.createPostcard(
      'ContactQuestsTest',
      'fixed test postcard',
      ['ContactQuestsTestTag'],
      'contact:default',
      false
    ).isEmpty()) fail('createPostcard returned an empty stack')

    if (!ContactQuests.createRedPacket(
      'ContactQuestsTest',
      'fixed test blessing',
      Item.of('minecraft:diamond')
    ) || ContactQuests.createRedPacket(
      'ContactQuestsTest',
      'fixed test blessing',
      Item.of('minecraft:diamond')
    ).isEmpty()) fail('createRedPacket returned an empty stack')

    // Invalid IDs must fail safely and must not break script loading.
    if (ContactQuests.editParcelTask('invalid-test-id', null, null, null)) {
      fail('editParcelTask accepted an invalid ID')
    }
    if (ContactQuests.editRedPacketTask('invalid-test-id', null, null, null, null)) {
      fail('editRedPacketTask accepted an invalid ID')
    }
    if (ContactQuests.editPostcardTask('invalid-test-id', null, null, null)) {
      fail('editPostcardTask accepted an invalid ID')
    }
    if (ContactQuests.editQuestTitle('invalid-test-id', 'ignored')) {
      fail('editQuestTitle accepted an invalid ID')
    }
    if (ContactQuests.editQuestSubtitle('invalid-test-id', 'ignored')) {
      fail('editQuestSubtitle accepted an invalid ID')
    }
    if (ContactQuests.editQuestDescription('invalid-test-id', ['ignored'])) {
      fail('editQuestDescription accepted an invalid ID')
    }
    if (ContactQuests.editParcelReward('invalid-test-id', null, null, null, null)) {
      fail('editParcelReward accepted an invalid ID')
    }
    if (ContactQuests.editRedPacketReward('invalid-test-id', null, null, null, null, null)) {
      fail('editRedPacketReward accepted an invalid ID')
    }
    if (ContactQuests.editPostcardReward('invalid-test-id', null, null, null)) {
      fail('editPostcardReward accepted an invalid ID')
    }
    if (ContactQuests.editParcelTaskSaved(level, 'invalid-test-id', null, null, null)) {
      fail('editParcelTaskSaved accepted an invalid ID')
    }
    if (ContactQuests.editRedPacketTaskSaved(level, 'invalid-test-id', null, null, null, null)) {
      fail('editRedPacketTaskSaved accepted an invalid ID')
    }
    if (ContactQuests.editPostcardTaskSaved(level, 'invalid-test-id', null, null, null)) {
      fail('editPostcardTaskSaved accepted an invalid ID')
    }
    if (ContactQuests.editParcelRewardSaved(level, 'invalid-test-id', null, null, null, null)) {
      fail('editParcelRewardSaved accepted an invalid ID')
    }
    if (ContactQuests.editRedPacketRewardSaved(level, 'invalid-test-id', null, null, null, null, null)) {
      fail('editRedPacketRewardSaved accepted an invalid ID')
    }
    if (ContactQuests.editPostcardRewardSaved(level, 'invalid-test-id', null, null, null)) {
      fail('editPostcardRewardSaved accepted an invalid ID')
    }
    if (ContactQuests.resetQuestTextSaved(level, 'invalid-test-id')) {
      fail('resetQuestTextSaved accepted an invalid ID')
    }
    pass('KubeJS item factories and invalid-ID safety checks completed')
  } catch (error) {
    fail(`KubeJS API smoke test threw: ${error}`)
  }

  ContactQuests.updateNpcDeliveryTime(level, TEST_NPCS.noTaskImmediate, 10)
  ContactQuests.updateNpcAutoFill(level, TEST_NPCS.noTaskImmediate, true)
  state.loaded = true
  TestResults.markLoaded()
  pass('KubeJS binding loaded and no-task NPC fixtures registered')
})

ContactEvents.registerReplacers(event => {
  event.registerPostcardTask('contactquests_test_postcard', (player, team) => 'fixed postcard replacement')
  event.registerRedPacketTask('contactquests_test_red_packet', (player, team) => 'fixed red packet replacement')
  event.registerPostcardReward((text, player) => `${text}|postcard`)
  event.registerRedPacketReward((text, player) => `${text}|red_packet`)
  event.registerNpcReply((text, context) => `${text}|npc_reply`)
  pass('KubeJS replacer registrations completed')
})

ContactEvents.mailReceived(event => {
  Java.loadClass('com.creeperyang.contactquests.test.ContactQuestsNewFeatureFixtures').recordLegacyMail(String(event.recipient))
  state.mailEvents++
  state.lastRecipient = String(event.recipient)

  try {
    if (!event.items || event.items.length === 0) {
      fail(`mailReceived had no items for ${event.recipient}`)
      return
    }

    if (String(event.recipient).startsWith('CQScriptMixed_')) {
      var diamonds = 0
      var dirt = 0
      event.items.forEach(item => {
        if (String(item.id) === 'minecraft:diamond') diamonds += item.getCount()
        if (String(item.id) === 'minecraft:dirt') dirt += item.getCount()
      })
      var exact = diamonds === 1 && dirt === 2
      TestResults.recordCase('kubejs.mixed.' + String(event.recipient), exact,
        `after task allocation: diamonds=${diamonds}, dirt=${dirt}`)
      event.intercept()
    }

    state.currentMailType = event.getMailType(event.items[0])
    // DataManager emits parcel contents rather than the outer ParcelItem for
    // delayed parcel delivery. Preserve the public helper call above while
    // classifying that fixed shape as a parcel for the result contract.
    if (state.currentMailType === 'unknown') {
      state.currentMailType = 'parcel'
    }
    if (!['parcel', 'postcard', 'red_packet'].includes(state.currentMailType)) {
      fail(`unexpected mail type ${state.currentMailType} for ${event.recipient}`)
    }

    // Exercise the public event helpers for every supported mail shape.
    var contents = event.getContainerContents(event.items[0])
    var blessing = String(event.getRedPacketBlessing(event.items[0]))
    var text = String(event.getPostcardText(event.items[0]))
    var style = String(event.getPostcardStyle(event.items[0]))
    if (String(event.recipient) === TEST_NPCS.aliasCandidate) {
      var helperOk = state.currentMailType === 'parcel'
        ? contents.isEmpty() && event.items.length === 1 && String(event.items[0].id) === 'minecraft:diamond' && event.items[0].getCount() === 1
        : state.currentMailType === 'red_packet'
          ? contents.size() === 1 && String(contents.get(0).id) === 'minecraft:gold_ingot' && contents.get(0).getCount() === 1 && blessing === 'fixed UI blessing'
          : text === 'fixed UI postcard' && style === 'contact:default'
      TestResults.recordCase('kubejs.mail.helpers.' + state.currentMailType, helperOk,
        `contents=${contents.size()}, blessing=${blessing}, text=${text}, style=${style}`)
    }

    // The fixed client scenario sends to a no-task KubeJS NPC. Interception
    // verifies reception of the remainder after task allocation.
    if ([TEST_NPCS.noTaskImmediate, TEST_NPCS.noTaskDelayed, TEST_NPCS.aliasCandidate]
        .includes(String(event.recipient))) {
      event.intercept()
      state.interceptedTypes[state.currentMailType] = true
    }

    TestResults.recordMail(state.currentMailType, String(event.recipient), event.isIntercepted())
    if (String(event.recipient) === TEST_NPCS.noTaskImmediate ||
        String(event.recipient) === TEST_NPCS.aliasCandidate) {
      pass(`mailReceived reached a no-task NPC (${state.currentMailType})`)
    }
  } catch (error) {
    fail(`mailReceived helper test threw: ${error}`)
  }
})

ServerEvents.tick(event => {
  if (!state.loaded) return

  // The client leaves the quest panel open while these real Rhino calls edit
  // the server. Separate stages prevent a SavedData call from hiding a broken
  // global edit, and verify reset restores the latest global translation.
  var textPhase = String(TestResults.consumeTextPhase())
  if (textPhase !== '') {
    try {
      var questId = TestResults.getRuntimeQuestId()
      var textLevel = event.server.overworld()
      var title = textPhase === 'saved' ? '脚本存档覆盖标题' : textPhase === 'global_after_saved' ? '脚本再次全局标题' : '脚本实时全局标题'
      var subtitle = textPhase === 'saved' ? '脚本存档覆盖副标题' : textPhase === 'global_after_saved' ? '脚本再次全局副标题' : '脚本实时全局副标题'
      var description = textPhase === 'saved' ? ['脚本存档覆盖描述', '覆盖第二行'] : textPhase === 'global_after_saved'
        ? ['脚本再次全局描述', '再次第二行'] : ['脚本实时全局描述', '全局第二行']
      for (var locale of ['en_us', 'zh_cn']) {
        if (textPhase === 'global') {
          if (!ContactQuests.resetQuestTextSaved(textLevel, questId, locale)) throw new Error('initial reset failed')
        }
        if (textPhase === 'saved') {
          if (!ContactQuests.editQuestTitleSaved(textLevel, questId, title, locale)
            || !ContactQuests.editQuestSubtitleSaved(textLevel, questId, subtitle, locale)
            || !ContactQuests.editQuestDescriptionSaved(textLevel, questId, description, locale)) throw new Error('saved text edit failed')
        } else if (textPhase === 'reset') {
          if (!ContactQuests.resetQuestTextSaved(textLevel, questId, locale)) throw new Error('text reset failed')
        } else {
          if (!ContactQuests.editQuestTitle(questId, title, locale)
            || !ContactQuests.editQuestSubtitle(questId, subtitle, locale)
            || !ContactQuests.editQuestDescription(questId, description, locale)) throw new Error('global text edit failed')
        }
      }
      TestResults.recordCase('kubejs.quest_text.live.' + textPhase, TestAssertions.liveQuestText(textLevel, textPhase),
        'Real Rhino phase ' + textPhase + '; both locales have exact title/subtitle/two lines and expected global/override maps')
    } catch (error) {
      TestResults.recordCase('kubejs.quest_text.live.' + textPhase, false, String(error))
    }
  }

  if (!state.graphEditDone && TestResults.hasRuntimeGraph()) {
    state.graphEditDone = true
    try {
      state.graphLevel = event.server.overworld()
      if (!ContactQuests.editParcelTask(
        TestResults.getRuntimeParcelTaskId(), Item.of('minecraft:gold_ingot'), 2, 'CQTest_ValidParcel'
      )) fail('valid editParcelTask returned false')
      if (!ContactQuests.editRedPacketTask(
        TestResults.getRuntimeRedPacketTaskId(), Item.of('minecraft:iron_ingot'), 2,
        'valid graph blessing', 'CQTest_ValidRedPacket'
      )) fail('valid editRedPacketTask returned false')
      if (!ContactQuests.editPostcardTask(
        TestResults.getRuntimePostcardTaskId(), 'contact:default',
        'valid graph postcard', 'CQTest_ValidPostcard'
      )) fail('valid editPostcardTask returned false')

      if (!ContactQuests.editParcelTaskSaved(
        state.graphLevel, TestResults.getRuntimeParcelTaskId(),
        Item.of('minecraft:emerald'), 3, 'CQTest_SavedParcel'
      )) fail('valid editParcelTaskSaved returned false')
      if (!ContactQuests.editRedPacketTaskSaved(
        state.graphLevel, TestResults.getRuntimeRedPacketTaskId(),
        Item.of('minecraft:coal'), 3, 'saved graph blessing', 'CQTest_SavedRedPacket'
      )) fail('valid editRedPacketTaskSaved returned false')
      if (!ContactQuests.editPostcardTaskSaved(
        state.graphLevel, TestResults.getRuntimePostcardTaskId(),
        'contact:default', 'saved graph postcard', 'CQTest_SavedPostcard'
      )) fail('valid editPostcardTaskSaved returned false')

      if (!ContactQuests.editParcelReward(
        TestResults.getRuntimeParcelRewardId(), Item.of('minecraft:apple'), 2, 1, 'CQTest_ValidParcel'
      )) fail('valid editParcelReward returned false')
      if (!ContactQuests.editRedPacketReward(
        TestResults.getRuntimeRedPacketRewardId(), Item.of('minecraft:diamond'), 2,
        'valid graph reward', 1, 'CQTest_ValidRedPacket'
      )) fail('valid editRedPacketReward returned false')
      if (!ContactQuests.editPostcardReward(
        TestResults.getRuntimePostcardRewardId(), 'contact:default',
        'valid graph reward postcard', 'CQTest_ValidPostcard'
      )) fail('valid editPostcardReward returned false')

      if (!ContactQuests.editParcelRewardSaved(
        state.graphLevel, TestResults.getRuntimeParcelRewardId(),
        Item.of('minecraft:bread'), 3, 2, 'CQTest_SavedParcel'
      )) fail('valid editParcelRewardSaved returned false')
      if (!ContactQuests.editRedPacketRewardSaved(
        state.graphLevel, TestResults.getRuntimeRedPacketRewardId(),
        Item.of('minecraft:lapis_lazuli'), 3, 'saved graph reward', 2, 'CQTest_SavedRedPacket'
      )) fail('valid editRedPacketRewardSaved returned false')
      if (!ContactQuests.editPostcardRewardSaved(
        state.graphLevel, TestResults.getRuntimePostcardRewardId(),
        'contact:default', 'saved graph reward postcard', 'CQTest_SavedPostcard'
      )) fail('valid editPostcardRewardSaved returned false')

      if (!ContactQuests.editQuestTitle(TestResults.getRuntimeQuestId(), 'valid graph title')) {
        fail('valid editQuestTitle returned false')
      }
      if (!ContactQuests.editQuestSubtitle(TestResults.getRuntimeQuestId(), 'valid graph subtitle')) {
        fail('valid editQuestSubtitle returned false')
      }
      if (!ContactQuests.editQuestDescription(TestResults.getRuntimeQuestId(), ['valid graph description'])) {
        fail('valid editQuestDescription returned false')
      }
      if (!ContactQuests.resetQuestTextSaved(state.graphLevel, TestResults.getRuntimeQuestId())) {
        fail('resetQuestTextSaved returned false')
      }
      if (!ContactQuests.editQuestTitleSaved(state.graphLevel, TestResults.getRuntimeQuestId(), 'valid graph title')) {
        fail('valid editQuestTitleSaved returned false')
      }
      if (!ContactQuests.editQuestSubtitleSaved(state.graphLevel, TestResults.getRuntimeQuestId(), 'valid graph subtitle')) {
        fail('valid editQuestSubtitleSaved returned false')
      }
      if (!ContactQuests.editQuestDescriptionSaved(state.graphLevel, TestResults.getRuntimeQuestId(), ['valid graph description'])) {
        fail('valid editQuestDescriptionSaved returned false')
      }
      ContactQuests.editQuestTitleSaved(state.graphLevel, TestResults.getRuntimeQuestId(), 'valid graph title', 'en_us')
      ContactQuests.editQuestSubtitleSaved(state.graphLevel, TestResults.getRuntimeQuestId(), 'valid graph subtitle', 'en_us')
      ContactQuests.editQuestDescriptionSaved(state.graphLevel, TestResults.getRuntimeQuestId(), ['valid graph description'], 'en_us')
      TestAssertions.verifyEditedGraph(state.graphLevel)
      pass('KubeJS valid task, reward, quest text, and SavedData edits completed')
    } catch (error) {
      fail(`KubeJS valid graph edit smoke test threw: ${error}`)
    }
  }

  if (!state.apiExerciseDone && event.server.playerList.getPlayers().size() > 0) {
    state.apiExerciseDone = true
    try {
      state.apiPlayer = event.server.playerList.getPlayers().get(0)
      state.apiLevel = event.server.overworld()
      state.apiTeam = ContactQuests.getData(state.apiPlayer)

      var added = ContactQuests.addTag(state.apiTeam, 'ContactQuestsTestTag')
      var present = ContactQuests.hasTag(state.apiTeam, 'ContactQuestsTestTag')
      var listed = ContactQuests.getTags(state.apiTeam).contains('ContactQuestsTestTag')
      var removed = ContactQuests.removeTag(state.apiTeam, 'ContactQuestsTestTag')
      TestResults.recordCase('kubejs.team_controls', added && present && listed && removed &&
        !ContactQuests.hasTag(state.apiTeam, 'ContactQuestsTestTag'),
        `tags: add=${added}, has=${present}, listed=${listed}, remove=${removed}`)

      ContactQuests.setNpcConfig(state.apiLevel, 'CQTest_ApiNpc', 5, false)
      ContactQuests.updateNpcDeliveryTime(state.apiLevel, 'CQTest_ApiNpc', 6)
      ContactQuests.updateNpcAutoFill(state.apiLevel, 'CQTest_ApiNpc', true)
      var npcSaved = TestAssertions.npcConfigRoundTrip(state.apiLevel, 'CQTest_ApiNpc', 6, true)
      ContactQuests.removeNpcConfig(state.apiLevel, 'CQTest_ApiNpc')
      TestResults.recordCase('kubejs.npc.config.round_trip', npcSaved &&
        TestAssertions.npcRemoved(state.apiLevel, 'CQTest_ApiNpc'), 'Updated delay/fill survived NBT reload; remove verified')

      ContactQuests.setNpcStash(state.apiPlayer, 'CQTest_ApiNpc', [Item.of('minecraft:apple')])
      var firstStash = ContactQuests.getNpcStash(state.apiPlayer, 'CQTest_ApiNpc')
      var stashSet = firstStash.size() === 1 && String(firstStash.get(0).id) === 'minecraft:apple'
      ContactQuests.addToNpcStash(state.apiPlayer, 'CQTest_ApiNpc', [Item.of('minecraft:paper')])
      var stashAdded = ContactQuests.getNpcStash(state.apiPlayer, 'CQTest_ApiNpc').size() === 2
      ContactQuests.clearNpcStash(state.apiPlayer, 'CQTest_ApiNpc')
      var stashCleared = ContactQuests.getNpcStash(state.apiPlayer, 'CQTest_ApiNpc').isEmpty()
      ContactQuests.setTriggerCount(state.apiPlayer, 'CQTest_ApiNpc', 2)
      TestResults.recordCase('kubejs.stash.controls', stashSet && stashAdded && stashCleared,
        `stash: set=${stashSet}, add=${stashAdded}, clear=${stashCleared}`)
      ContactQuests.setNpcStash(state.apiPlayer, 'CQTest_PersistentStash', [Item.of('3x minecraft:apple')])
      ContactQuests.addToNpcStash(state.apiPlayer, 'CQTest_PersistentStash', [Item.of('2x minecraft:paper')])
      ContactQuests.setTriggerCount(state.apiPlayer, 'CQTest_PersistentStash', 2)
      TestResults.recordCase('kubejs.stash.persistence',
        TestAssertions.stashRoundTrip(state.apiPlayer, 'CQTest_PersistentStash'),
        'NBT reload: apple x3, paper x2, triggerCount=2')

      ContactQuests.setTriggerCount(state.apiPlayer, 'CQTest_CountApi', 7)
      var countPlayer = TestAssertions.npcTriggerCount(state.apiPlayer, 'CQTest_CountApi') === 7
      ContactQuests.setTriggerCount(state.apiLevel, state.apiPlayer.uuid, 'CQTest_CountApi', 9)
      var countUuid = TestAssertions.npcTriggerCount(state.apiPlayer, 'CQTest_CountApi') === 9
      ContactQuests.setNpcStash(state.apiPlayer, 'CQTest_CountApi', [Item.of('3x minecraft:apple')])
      ContactQuests.addToNpcStash(state.apiPlayer, 'CQTest_CountApi', [Item.of('2x minecraft:paper')])
      ContactQuests.clearNpcStash(state.apiPlayer, 'CQTest_CountApi')
      TestResults.recordCase('kubejs.npc.trigger_count', countPlayer && countUuid &&
        TestAssertions.npcTriggerCount(state.apiPlayer, 'CQTest_CountApi') === 9 &&
        TestAssertions.triggerCountRoundTrip(state.apiPlayer, 'CQTest_CountApi', 9),
        'Player and level/UUID overloads set exact 7/9; silent stash controls keep count=9 through reload')

      ContactQuests.forceQuest(state.apiTeam, 'invalid-test-id')
      ContactQuests.unforceQuest(state.apiTeam, 'invalid-test-id')
      ContactQuests.blockQuest(state.apiTeam, 'invalid-test-id')
      ContactQuests.unblockQuest(state.apiTeam, 'invalid-test-id')
      ContactQuests.resetQuestState(state.apiTeam, 'invalid-test-id')
      ContactQuests.openQuest(state.apiPlayer, '999999999999')

      var factoryCard = ContactQuests.createPostcard('ContactQuestsTest', 'fixed test postcard',
        ['ContactQuestsTestTag'], 'contact:default', false)
      var factoryRed = ContactQuests.createRedPacket('ContactQuestsTest', 'fixed test blessing', Item.of('2x minecraft:diamond'))
      TestResults.recordCase('kubejs.item_factory.components', TestAssertions.factories(factoryCard, factoryRed, state.apiPlayer),
        'Factory card sender/text/style/tags and red packet blessing/content x2')
      TestResults.recordCase('kubejs.replacers', TestAssertions.replacers(state.apiPlayer),
        'Both task and both reward placeholders plus NPC reply transformed to expected strings')
      ContactQuests.syncAllOverridesToPlayer(state.apiPlayer)
      ContactQuests.refreshQuests()
      pass('KubeJS team, NPC, stash, state, send, and refresh APIs completed')
    } catch (error) {
      fail(`KubeJS valid API smoke test threw: ${error}`)
    }
  }

  if (!state.stateControlsDone && state.apiTeam && TestResults.hasRuntimeStateGraph()) {
    state.stateControlsDone = true
    try {
      var stateQuestId = String(TestResults.getRuntimeStateQuestHexId())
      var numericQuestId = TestResults.getRuntimeStateQuestId()
      state.apiTeam = ContactQuests.getData(state.apiPlayer)
      var forced = ContactQuests.forceQuest(state.apiTeam, stateQuestId)
      var forcedState = TestAssertions.questForced(state.apiTeam, numericQuestId) && !TestAssertions.questBlocked(state.apiTeam, numericQuestId)
      var unforced = ContactQuests.unforceQuest(state.apiTeam, stateQuestId)
      var unforcedState = !TestAssertions.questForced(state.apiTeam, numericQuestId)
      var blocked = ContactQuests.blockQuest(state.apiTeam, stateQuestId)
      var blockedState = TestAssertions.questBlocked(state.apiTeam, numericQuestId) && !TestAssertions.questForced(state.apiTeam, numericQuestId)
      var unblocked = ContactQuests.unblockQuest(state.apiTeam, stateQuestId)
      var unblockedState = !TestAssertions.questBlocked(state.apiTeam, numericQuestId)
      ContactQuests.forceQuest(state.apiTeam, stateQuestId)
      ContactQuests.resetQuestState(state.apiTeam, stateQuestId)
      var resetState = !TestAssertions.questForced(state.apiTeam, numericQuestId) && !TestAssertions.questBlocked(state.apiTeam, numericQuestId)
      ContactQuests.openQuest(state.apiPlayer, stateQuestId)
      TestResults.recordCase('kubejs.quest.state_controls', forced && unforced && blocked && unblocked &&
        forcedState && unforcedState && blockedState && unblockedState && resetState,
        `Actual flags force=${forcedState}, unforce=${unforcedState}, block=${blockedState}, unblock=${unblockedState}, reset=${resetState}`)
    } catch (error) {
      fail(`KubeJS valid quest state controls threw: ${error}`)
    }
  }

  if (!state.deliveryDone && state.apiPlayer && TestResults.isCasePassed('server.team_binding.full_flow')) {
    state.deliveryDone = true
    var beforeDelivery = TestAssertions.mailboxAmounts(state.apiPlayer)
    ContactQuests.sendMail(state.apiPlayer, 'ContactQuestsTest',
      ContactQuests.createRedPacket('ContactQuestsTest', 'api smoke', Item.of('2x minecraft:paper')), false)
    ContactQuests.sendParcel(state.apiPlayer, 'ContactQuestsTest', [Item.of('3x minecraft:apple')], false)
    TestAssertions.verifyScriptDelivery(state.apiPlayer, beforeDelivery[0], beforeDelivery[1])
  }

  if (!state.configClearExerciseDone && state.interceptedTypes.parcel &&
      state.interceptedTypes.red_packet && state.interceptedTypes.postcard) {
    state.configClearExerciseDone = true
    try {
      ContactQuests.clearNpcConfigs(state.level)
      TestResults.recordCase('kubejs.npc.config.clear', TestAssertions.npcConfigsEmpty(state.level),
        'clearNpcConfigs removed all script NPC configs')
      pass('KubeJS NPC config clear API completed after mail assertions')
    } catch (error) {
      fail(`KubeJS NPC config clear API threw: ${error}`)
    }
  }

  if (event.server.tickCount % 20 !== 0) return

  if (state.failures.length > 0) {
    event.server.persistentData.putBoolean('contactquests_test_failed', true)
    event.server.persistentData.putString('contactquests_test_failure', state.failures.join('; '))
  } else {
    event.server.persistentData.putBoolean('contactquests_test_loaded', true)
  }
})
})()
