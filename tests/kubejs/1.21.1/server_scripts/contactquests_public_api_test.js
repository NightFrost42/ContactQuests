// Exercise public overloads through Rhino and inspect effects independently.
(function () {
  var Results = Java.loadClass('com.creeperyang.contactquests.test.ContactQuestsTestServerController')
  var Assertions = Java.loadClass('com.creeperyang.contactquests.test.ContactQuestsPublicApiAssertions')
  var Saved = Java.loadClass('com.creeperyang.contactquests.compat.kubejs.QuestOverrideSavedData')
  var ran = false
  function check(value, message) { if (!value) throw new Error(message) }
  ServerEvents.tick(event => {
    if (Java.loadClass('java.lang.Boolean').getBoolean('contactquests.test.rejoin')) return
    if (ran || !Results.isCasePassed('server.commands.fixed_results') || !Results.isCasePassed('kubejs.stash.persistence')) return
    ran = true
    try {
      var level = event.server.overworld(), player = event.server.playerList.getPlayers().get(0)
      var defaults = ContactQuests.createChapter(level, 'cqtest:public_api/default')
      check(String(defaults.rawTitle) === '', 'Omitted chapter title did not use native default')
      var chapter = ContactQuests.createChapter(level, 'cqtest:public_api/main', 'API title', {subtitle:['API subtitle'], defaultQuestSize:1.25})
      var chapterId = String(chapter.codeString)
      check(ContactQuests.getChapter(level, chapterId) === chapter && String(chapter.rawTitle) === 'API title'
        && chapter.rawSubtitle.contains('API subtitle'), 'Explicit title/options chapter overload ignored fields')
      var quest = ContactQuests.createQuest(level, chapterId, 'cqtest:public_api/quest', 'API quest', {x:4,y:-3})
      var id = String(quest.codeString)
      check(Number(quest.x) === 4 && Number(quest.y) === -3 && String(quest.rawTitle) === 'API quest', 'Explicit title/options quest overload ignored fields')
      var blank = ContactQuests.createQuest(level, chapterId, 'cqtest:public_api/blank', null, null)
      check(String(blank.rawTitle) === '' && Number(blank.x) === 0, 'Null explicit title/options did not default')
      var nullChapter = ContactQuests.createChapter(level, 'cqtest:public_api/null_chapter', null, null)
      var emptyBundle = ContactQuests.publishQuest(level, String(nullChapter.codeString), 'cqtest:public_api/null_bundle', null, null, null)
      check(String(nullChapter.rawTitle) === '' && String(emptyBundle.rawTitle) === ''
        && emptyBundle.tasks.isEmpty() && emptyBundle.rewards.isEmpty(), 'Rhino null object/list defaults were rejected or changed')
      var whitespace = ContactQuests.createChapter(level, 'cqtest:public_api/whitespace', '  ', '  ')
      var whitespaceQuest = ContactQuests.createQuest(level, String(whitespace.codeString), 'cqtest:public_api/whitespace_quest', '', ' ')
      check(String(whitespace.rawTitle) === '' && String(whitespaceQuest.rawTitle) === '', 'Blank whole options/title did not default')
      var rewardDefault = ContactQuests.addReward(level, String(blank.codeString), 'cqtest:public_api/default_reward', 'contactquests:parcel')
      check(Number(rewardDefault.count) === 1, 'Omitted reward NBT did not default')
      var before = ContactQuests.listChapters(level).size(), rejected = false
      try { ContactQuests.createChapter(level, 'cqtest:public_api/bad', 17) } catch(expected) { rejected = true }
      check(rejected && ContactQuests.listChapters(level).size() === before, 'Invalid options created a partial chapter')
      Results.recordCase('kubejs.public_api.creation_overloads', true, 'Real Rhino omitted/title/options/null overloads, lookup, default reward and atomic invalid argument checks')

      var Boundaries = Java.loadClass('com.creeperyang.contactquests.test.ContactQuestsRhinoBoundaryAssertions')
      var Map = Java.loadClass('java.util.HashMap')
      var invalidKey = new Map(); invalidKey.put(Java.loadClass('java.lang.Integer').valueOf(1), 'invalid')
      var invalidCalls = [
        () => ContactQuests.createChapter(level,'cqtest:public_api/bad_map',invalidKey),
        () => ContactQuests.createChapter(level,'cqtest:public_api/bad_options','title',1),
        () => ContactQuests.createQuest(level,chapterId,'cqtest:public_api/bad_map',invalidKey),
        () => ContactQuests.createQuest(level,chapterId,'cqtest:public_api/bad_options','title',1),
        () => ContactQuests.createQuest(level,chapterId,'cqtest:public_api/bad_keys','title',invalidKey),
        () => ContactQuests.publishQuest(level,chapterId,'cqtest:public_api/bad_bundle',1,[],[]),
        () => ContactQuests.publishQuest(level,chapterId,'cqtest:public_api/bad_bundle',{},1,[]),
        () => ContactQuests.publishQuest(level,chapterId,'cqtest:public_api/bad_bundle',{},[1],[]),
        () => ContactQuests.publishQuest(level,chapterId,'cqtest:public_api/bad_bundle',{},[invalidKey],[]),
        () => ContactQuests.publishQuest(level,chapterId,'cqtest:public_api/bad_bundle',{},[],1),
        () => ContactQuests.publishQuest(level,chapterId,'cqtest:public_api/bad_bundle',{},[],[1])
      ]
      for (var invalid of invalidCalls) {
        var boundaryBefore = Boundaries.snapshot(level)
        rejected = false
        try { invalid() } catch(expected) { rejected = true }
        check(rejected && boundaryBefore.equals(Boundaries.snapshot(level)), 'Invalid public options/children changed native graph, ID links, overrides or saved files')
      }
      var missingBefore = Boundaries.snapshot(level)
      check(!ContactQuests.editQuestDescriptionSaved(level,'5EED000000000001',['missing quest'], 'en_us')
        && missingBefore.equals(Boundaries.snapshot(level)), 'Missing quest description created a persisted override')
      Results.recordCase('kubejs.public_api.invalid_shapes_atomic', true, 'Real Rhino rejects wrong option/children/member types and non-string keys; native graph, ID links, overrides and saved files stay identical')

      for (var invalidDescription of [17, 'not an array', ['valid first line', 1]]) {
        var descriptionBefore = Boundaries.snapshot(level)
        rejected = false
        try { ContactQuests.editQuestDescription(id, invalidDescription, 'en_us') } catch(expected) { rejected = true }
        check(rejected && descriptionBefore.equals(Boundaries.snapshot(level)), 'Invalid global description changed a valid quest or its persisted text')
        rejected = false
        try { ContactQuests.editQuestDescriptionSaved(level, id, invalidDescription, 'en_us') } catch(expected) { rejected = true }
        check(rejected && descriptionBefore.equals(Boundaries.snapshot(level)), 'Invalid saved description changed a valid quest or its persisted text')
      }
      Results.recordCase('kubejs.public_api.description_rejection_atomic', true, 'Both live and saved description APIs reject numbers, scalar strings and mixed arrays on an existing quest; native graph, translations, override data and disk stay exact')

      var parcel = ContactQuests.addTask(level,id,'cqtest:public_api/parcel','contactquests:parcel_task',{item:'minecraft:diamond',count:2,TargetAddressee:'CQApiInitial'})
      var red = ContactQuests.addTask(level,id,'cqtest:public_api/red','contactquests:red_packet_task',{item:'minecraft:gold_ingot',count:3,blessing:'initial',TargetAddressee:'CQApiInitial'})
      var card = ContactQuests.addTask(level,id,'cqtest:public_api/card','contactquests:postcard_task',{postcard_style:'contact:default',postcard_text:'initial',TargetAddressee:'CQApiInitial'})
      var parcelReward = ContactQuests.addReward(level,id,'cqtest:public_api/parcel_reward','contactquests:parcel',{item:'minecraft:apple',count:2,target_addressee:'CQApiInitial'})
      var redReward = ContactQuests.addReward(level,id,'cqtest:public_api/red_reward','contactquests:red_packet',{item:'minecraft:diamond',count:2,blessing:'initial',target_addressee:'CQApiInitial'})
      var cardReward = ContactQuests.addReward(level,id,'cqtest:public_api/card_reward','contactquests:postcard',{postcard_style:'contact:default',postcard_text:'initial',target_addressee:'CQApiInitial'})
      var p = String(parcel.codeString), r = String(red.codeString), c = String(card.codeString)
      var pr = String(parcelReward.codeString), rr = String(redReward.codeString), cr = String(cardReward.codeString)
      var Compound = Java.loadClass('net.minecraft.nbt.CompoundTag')
      var typedCount = new Compound(); typedCount.putDouble('count',4.0)
      check(ContactQuests.updateTask(level,p,typedCount) && Number(parcel.count) === 4, 'Integral DoubleTag count was not accepted exactly')
      typedCount = new Compound(); typedCount.putFloat('count',5.0)
      check(ContactQuests.updateTask(level,p,typedCount) && Number(parcel.count) === 5, 'Integral FloatTag count was not accepted exactly')
      var fractionalBefore = Boundaries.snapshot(level)
      typedCount = new Compound(); typedCount.putDouble('count',4.5); rejected = false
      try { ContactQuests.updateTask(level,p,typedCount) } catch(expected) { rejected = true }
      check(rejected && Number(parcel.count) === 5 && fractionalBefore.equals(Boundaries.snapshot(level)), 'Fractional DoubleTag partially changed the graph')
      Results.recordCase('kubejs.public_api.native_numeric_tags', true, 'Explicit integral DoubleTag/FloatTag counts normalize exactly; fractional NBT leaves graph and persistent records unchanged')
      check(ContactQuests.editParcelTaskSaved(level,p,Item.of('minecraft:emerald'),5,'CQApiSavedParcel'), 'Saved parcel edit failed')
      check(ContactQuests.editRedPacketTaskSaved(level,r,Item.of('minecraft:iron_ingot'),6,'saved blessing','CQApiSavedRed'), 'Saved red packet edit failed')
      check(ContactQuests.editPostcardTaskSaved(level,c,'contact:creeper','saved postcard','CQApiSavedCard'), 'Saved postcard edit failed')
      check(ContactQuests.editParcelRewardSaved(level,pr,Item.of('minecraft:bread'),7,2,'CQApiSavedParcel'), 'Saved parcel reward edit failed')
      check(ContactQuests.editRedPacketRewardSaved(level,rr,Item.of('minecraft:lapis_lazuli'),8,'saved reward blessing',3,'CQApiSavedRed'), 'Saved red packet reward edit failed')
      check(ContactQuests.editPostcardRewardSaved(level,cr,'contact:creeper','saved reward card','CQApiSavedCard'), 'Saved postcard reward edit failed')
      var serializedBefore = String(Saved.Companion.get(level).getOverride(parcel.id))
      check(!ContactQuests.editParcelTaskSaved(level,p,Item.of('minecraft:emerald'),5,'CQApiSavedParcel'), 'Identical saved edit was not a no-op')
      check(!ContactQuests.editRedPacketTaskSaved(level,r,null,null,null,null), 'Null saved red edit was not a no-op')
      check(!ContactQuests.editPostcardTaskSaved(level,c,null,null,null), 'Null saved card edit was not a no-op')
      check(!ContactQuests.editParcelRewardSaved(level,pr,null,null,null,null), 'Null saved reward edit was not a no-op')
      check(!ContactQuests.editRedPacketRewardSaved(level,rr,null,null,null,null,null), 'Null saved red reward edit was not a no-op')
      check(!ContactQuests.editPostcardRewardSaved(level,cr,null,null,null), 'Null saved card reward edit was not a no-op')
      check(String(Saved.Companion.get(level).getOverride(parcel.id)) === serializedBefore, 'No-op changed serialized override')
      var questBefore = Assertions.questTag(player,id)
      check(!ContactQuests.editQuestTitle(id) && !ContactQuests.editQuestSubtitle(id) && !ContactQuests.editQuestDescription(id), 'Omitted global text changed graph')
      check(!ContactQuests.editQuestTitleSaved(level,id) && !ContactQuests.editQuestSubtitleSaved(level,id)
        && !ContactQuests.editQuestDescriptionSaved(level,id,null), 'Omitted/null saved text changed graph')
      check(questBefore.equals(Assertions.questTag(player,id)), 'No-op text call changed native quest NBT')
      Results.recordCase('kubejs.public_api.noop_edits', true, 'Same-value/null/omitted calls preserve native object and SavedData rather than silently resetting fields')
      var simple = ContactQuests.createPostcard('', '', [], null, false)
      var ender = ContactQuests.createPostcard('CQApiSender', 'one\\ntwo', [], '', true)
      check(Assertions.factories(simple,ender), 'Factory blank/default/ender/newline components incorrect')
      Results.recordCase('kubejs.public_api.factory_defaults', true, 'Real Contact normal/ender postcards retain default style, no invented sender/tags and correct newline text')
      Assertions.verifyAndCapture(player,id)
    } catch(error) {
      Results.recordCase('kubejs.public_api.failure', false, String(error))
      console.error('[contactquests_public_api_test] '+error)
    }
  })
})()
