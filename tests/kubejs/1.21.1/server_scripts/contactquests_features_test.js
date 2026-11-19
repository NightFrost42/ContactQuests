// Public creation/options calls execute in the real Rhino runtime, with actual native NBT and lists.
(function () {
  var Results = Java.loadClass('com.creeperyang.contactquests.test.ContactQuestsTestServerController')
  var Fixtures = Java.loadClass('com.creeperyang.contactquests.test.ContactQuestsNewFeatureFixtures')
  var ran = false
  function check(value, message) { if (!value) throw new Error(message) }
  ServerEvents.tick(event => {
    if (Java.loadClass('java.lang.Boolean').getBoolean('contactquests.test.rejoin')) return
    if (ran || !Results.isCasePassed('kubejs.npc.no_task.immediate')) return
    ran = true
    var level = event.server.overworld()
    try {
      var chapter = ContactQuests.createChapter(level, 'cqtest:features/rhino', {
        title:'Creation chapter', subtitle:['Creation subtitle'], defaultQuestShape:'circle', defaultQuestSize:1.5,
        defaultMinWidth:220, disableToast:true, progressionMode:'LINEAR', requireSequentialTasks:true,
        defaultRepeatable:false, hideTextUntilComplete:false, hideQuestUntilDepsComplete:true,
        hideQuestDetailsUntilStartable:true, consumeItems:'DEFAULT', icon:'minecraft:book', tags:['CreationChapter']
      })
      var chapterId = String(chapter.codeString)
      var defaultQuest = ContactQuests.createQuest(level, chapterId, 'cqtest:features/rhino_default', {
        title:'', subtitle:null, description:null, x:null, y:null, shape:'  ', size:null
      })
      check(Number(defaultQuest.x) === 0 && Number(defaultQuest.y) === 0 && String(defaultQuest.rawTitle) === '', 'Empty creation options did not use defaults')
      var defaultTask = ContactQuests.addTask(level, String(defaultQuest.codeString), 'cqtest:features/rhino_default_task', 'contactquests:parcel_task', {count:null, TargetAddressee:'  '})
      var defaultReward = ContactQuests.addReward(level, String(defaultQuest.codeString), 'cqtest:features/rhino_default_reward', 'contactquests:parcel', {item:'', count:null, hide_contents:null})
      check(Number(defaultTask.count) === 1 && Number(defaultReward.count) === 1 && !defaultReward.hideContents, 'Task/reward blanks did not inherit defaults')
      check(String(ContactQuests.createQuest(level, chapterId, 'cqtest:features/rhino_default').codeString) === String(defaultQuest.codeString), 'Omitted options changed stable quest ID')
      check(String(ContactQuests.createQuest(level, chapterId, 'cqtest:features/rhino_default', null).codeString) === String(defaultQuest.codeString), 'Null options selected an ambiguous overload')
      check(String(ContactQuests.createChapter(level, 'cqtest:features/rhino', null).codeString) === chapterId, 'Null chapter options selected an ambiguous overload')
      Results.recordCase('kubejs.features.creation_defaults', true, 'Rhino omitted/null/empty/whitespace fields retain native defaults and stable keys')

      var dep = ContactQuests.createQuest(level, chapterId, 'cqtest:features/rhino_dep', {title:'Dependency'})
      ContactQuests.addTask(level, String(dep.codeString), 'cqtest:features/rhino_dep_task', 'ftbquests:checkmark')
      var depId = String(dep.codeString)
      var points = {}; points[depId] = [[1,2],[3,4]]
      var quest = ContactQuests.publishQuest(level, chapterId, 'cqtest:features/rhino_bundle', {
        title:'Creation bundle', subtitle:'Creation quest subtitle', description:['One','Two'], x:6,y:7,
        requiredTags:['CreationTag'], tagMode:'ALL', mutex:[depId], mutexMode:'NUMBER', mutexCount:1, dependencies:[depId],
        dependencyControlPoints:points, dependencyRequirement:'all_completed', hideWithoutTags:true,hideIfLocked:true,
        size:2,iconScale:1.2,minWidth:240,shape:'circle',guidePage:'creation',icon:'minecraft:apple',tags:['CreationQuest'],
        hideUntilDepsVisible:'DEFAULT',hideUntilDepsComplete:false,hideDetailsUntilStartable:true,
        hideTextUntilComplete:false,hideDependencyLines:false,hideDependentLines:true,hideLockIcon:true,
        disableRecipeMod:false,canRepeat:false,requireSequentialTasks:true,repeatCooldown:8,
        optional:true,invisible:false,invisibleUntilTasks:0,minRequiredDependencies:1,maxCompletableDependents:2,
        ignoreRewardBlocking:true,disableToast:true
      }, [
        {type:'contactquests:parcel_task',data:{title:'Parcel title',item:'minecraft:diamond',count:5,TargetAddressee:'CQCreation',optional_task:true,disable_toast:true}},
        {type:'contactquests:red_packet_task',data:{item:'minecraft:gold_ingot',count:2,blessing:'Creation blessing',match_components:'STRICT'}},
        {type:'contactquests:postcard_task',data:{postcard_style:'contact:creeper',postcard_text:'Creation text',count:1}}
      ], [
        {type:'contactquests:parcel',data:{item:'minecraft:apple',count:3,hide_contents:true,team_reward:'DEFAULT',auto:'disabled',exclude_from_claim_all:true}},
        {type:'contactquests:red_packet',data:{item:'minecraft:emerald',count:2,blessing:'Creation reward',hide_contents:true}},
        {type:'contactquests:postcard',data:{postcard_style:'contact:creeper',postcard_text:'Creation card',hide_contents:true}},
        {type:'contactquests:parcel_choice',data:{hide_contents:true,template_group:'default',option_labels:['Alpha','Beta'],table_data:{rewards:[
          {type:'ftbquests:item',item:'minecraft:apple',title:'Creation apple option',count:3,weight:1}, {type:'ftbquests:item',item:'minecraft:diamond',title:'Creation diamond option',count:7,weight:3}
        ]}}}
      ])
      check(Number(quest.x) === 6 && Number(quest.y) === 7 && String(quest.rawTitle) === 'Creation bundle'
        && quest.tasks.size() === 3 && quest.rewards.size() === 4, 'Complete bundle fields or children were not created')
      var choice = quest.rewards.stream().filter(r => String(r.type.typeForNBT) === 'contactquests:parcel_choice').findFirst().orElse(null)
      check(choice !== null && choice.hideContents && String(choice.templateGroup) === 'default' && choice.optionLabels.size() === 2 && choice.table.weightedRewards.size() === 2, 'New reward options were ignored')
      check(String(ContactQuests.createQuest(level, chapterId, 'cqtest:features/rhino_bundle', {title:'ignored'}).rawTitle) === 'Creation bundle', 'Idempotent create silently upserted')
      Results.recordCase('kubejs.features.creation_bundle', true, 'Rhino creates native graph/display/dependency controls plus task/reward configs and new choice/hiding fields together')

      var before = ContactQuests.listQuests(level,chapterId).size(), rejected = false
      try { ContactQuests.publishQuest(level,chapterId,'cqtest:features/rhino_invalid',{title:'Half quest',dependencies:[depId]},[
        {type:'contactquests:parcel_task',data:{item:'minecraft:diamond',count:5}},
        {type:'contactquests:red_packet_task',data:{count:-1}}
      ],[{type:'contactquests:parcel'}]) } catch(expected) { rejected = true }
      check(rejected && ContactQuests.listQuests(level,chapterId).size() === before, 'Invalid bundle left partial graph')
      ContactQuests.updateQuest(level,String(quest.codeString),{tags:[]})
      check(Number(quest.x) === 6 && Number(quest.y) === 7, 'Omitted update fields were reset')
      Java.loadClass('com.creeperyang.contactquests.test.ContactQuestsCreationAssertions').capture(chapter, quest)
      Results.recordCase('kubejs.features.atomic_validation', true, 'Invalid full bundle leaves no children/quest, updates keep omitted fields and empty lists clear')
    } catch(error) {
      Results.recordCase('kubejs.features.creation_failure',false,String(error))
      console.error('[contactquests_features_test] '+error)
    }
  })
  var eventsRan = false
  ServerEvents.tick(event => {
    if (Java.loadClass('java.lang.Boolean').getBoolean('contactquests.test.rejoin')) return
    if (eventsRan || !Results.isCasePassed('server.features.prepare')) return
    var players = event.server.playerList.getPlayers()
    if (players.isEmpty()) return
    eventsRan = true
    try {
      var apiPlayer = players.get(0)
      var eventId = String(Fixtures.prepareStateEvents(apiPlayer))
      var eventTeam = ContactQuests.getData(apiPlayer)
      ContactQuests.addTag(eventTeam,'CQFeatureEvent')
      ContactQuests.addTag(eventTeam,'CQFeatureEvent')
      ContactQuests.blockQuest(eventTeam,eventId)
      ContactQuests.blockQuest(eventTeam,eventId)
      ContactQuests.unblockQuest(eventTeam,eventId)
      Fixtures.mutateStateAfterRhinoGrant(apiPlayer)
      Fixtures.verifyStateEvents(apiPlayer)
    } catch(error) {
      Results.recordCase('kubejs.events.native_and_script',false,String(error))
      console.error('[contactquests_features_events_test] '+error)
    }
  })
})()
