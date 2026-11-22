// Normal graph options and explicit empty descriptions through the real Rhino binding.
// Runs after the existing command/public-API chain; a new JVM only reads the captured graph.
(function () {
  var Results = Java.loadClass('com.creeperyang.contactquests.test.ContactQuestsTestServerController')
  var Assertions = Java.loadClass('com.creeperyang.contactquests.test.ContactQuestsGraphOptionsAssertions')
  var BooleanClass = Java.loadClass('java.lang.Boolean')
  var ran = false
  var prefix = 'cqtest:graph-options/'
  function check(value, message) { if (!value) throw new Error(message) }
  function code(object) { return String(object.codeString) }
  function icon(item, name, marker) {
    // QuestGraphSettings.item uses ItemStack.of: Count and tag are native 1.20.1 NBT.
    return {id:item, Count:9, tag:{
      display:{Name:'{"text":"' + name + '","color":"gold","bold":true}'},
      cq_graph_options:marker, sentinel:'keep-exact'
    }}
  }
  function rejectPresetAtomic(level, action) {
    var before = Assertions.snapshot(level), rejected = false
    try { action() } catch (expected) {
      rejected = String(expected).indexOf('Visual presets are unavailable in FTB Quests 2001.4.22') !== -1
    }
    check(rejected && before.equals(Assertions.snapshot(level)),
      'Unsupported visual preset did not reject cleanly without changing graph/native text/overrides/disk')
  }
  function rejectAtomic(level, quest, dependency) {
    var before = Assertions.snapshot(level), rejected = false
    try {
      ContactQuests.updateQuest(level, code(quest), {
        title:'MUST NOT CHANGE', customId:'cqtest:graph-options/rejected',
        description:['MUST NOT CHANGE'], dependencies:[code(dependency)]
      })
    } catch (expected) { rejected = true }
    check(rejected && before.equals(Assertions.snapshot(level)),
      'Cycle did not reject atomically across native graph/index/dependants/text/overrides/disk')
  }
  ServerEvents.tick(event => {
    if (BooleanClass.getBoolean('contactquests.test.rejoin') || ran
      || Results.hasCase('kubejs.graph_options.persisted') || Results.hasCase('kubejs.graph_options.failure')) return
    if (!Results.isCasePassed('server.commands.fixed_results') || !Results.isCasePassed('kubejs.public_api.saved_reload')) return
    if (event.server.playerList.getPlayers().isEmpty()) return
    ran = true
    var currentCase = 'kubejs.graph_options.custom_id_autofocus'
    try {
      var player = event.server.playerList.getPlayers().get(0), level = event.server.overworld()
      Assertions.prepare(player)
      var chapter = ContactQuests.createChapter(level, prefix + 'main', {
        title:'Graph options chapter', locale:'en_us', customId:prefix+'chapter-created',
        defaultQuestShape:'circle', defaultQuestSize:1.25, icon:icon('minecraft:diamond','Graph chapter icon','chapter')
      })
      var other = ContactQuests.createChapter(level, prefix + 'dependency', {title:'Graph dependency chapter',locale:'en_us'})
      var prerequisite = ContactQuests.createQuest(level, code(other), prefix + 'prerequisite', {title:'Graph prerequisite',locale:'en_us'})
      var prerequisiteTask = ContactQuests.addTask(level, code(prerequisite), prefix+'prerequisite-task', 'ftbquests:checkmark', {})
      var focus = ContactQuests.createQuest(level, code(chapter), prefix + 'focus', {
        title:'Graph options focus', locale:'en_us', customId:prefix+'quest-created', shape:'square', size:1.75,
        x:6, y:-4, icon:icon('minecraft:emerald','Graph quest icon','quest'), dependencies:[code(prerequisiteTask)]
      })
      var focusTask = ContactQuests.addTask(level, code(focus), prefix+'focus-task', 'ftbquests:checkmark', {})
      var inherited = ContactQuests.createQuest(level, code(chapter), prefix + 'inherited', {title:'Graph inherited preset',locale:'en_us',x:2,y:3})
      var consumer = ContactQuests.createQuest(level, code(chapter), prefix + 'chapter-consumer', {
        title:'Graph chapter dependency',locale:'en_us',dependencies:[code(other)]
      })
      Assertions.createdIds(player)
      check(ContactQuests.updateChapter(level, code(chapter), {customId:prefix+'chapter',autofocusId:code(focus)}), 'Legal chapter options rejected')
      check(ContactQuests.updateQuest(level, code(focus), {customId:prefix+'quest'}), 'Legal quest customId rejected')
      currentCase = 'kubejs.graph_options.presets'
      // Keep the shared case ID: 2001.4.22 supports direct shape/size, but no visual presets.
      rejectPresetAtomic(level, () => ContactQuests.createChapter(level, prefix+'rejected-chapter', {
        title:'MUST NOT CREATE', defaultPreset:prefix+'default'
      }))
      rejectPresetAtomic(level, () => ContactQuests.createQuest(level, code(chapter), prefix+'rejected-quest', {
        title:'MUST NOT CREATE', preset:prefix+'explicit'
      }))
      rejectPresetAtomic(level, () => ContactQuests.updateChapter(level, code(chapter), {
        title:'MUST NOT CHANGE', defaultPreset:prefix+'default'
      }))
      rejectPresetAtomic(level, () => ContactQuests.updateQuest(level, code(focus), {
        title:'MUST NOT CHANGE', preset:prefix+'explicit'
      }))
      check(String(inherited.shape) === 'circle' && Number(inherited.size) === 1.25
        && String(focus.shape) === 'square' && Number(focus.size) === 1.75,
        'Direct chapter shape/size inheritance or explicit quest shape/size changed')
      Assertions.verifyOptions(player)
      Results.recordCase(currentCase, true, 'FTB Quests 2001.4.22 rejects nonempty presets atomically; direct defaults inherit circle/1.25 and quest options retain square/1.75')

      currentCase = 'kubejs.graph_options.task_cycle_atomic'
      rejectAtomic(level, prerequisite, focusTask)
      Results.recordCase(currentCase, true, 'Rhino Task dependency cycle rejected; native graph/index/dependants/text/overrides and disk unchanged')
      currentCase = 'kubejs.graph_options.chapter_cycle_atomic'
      rejectAtomic(level, prerequisite, chapter)
      Results.recordCase(currentCase, true, 'Rhino Chapter dependency cycle rejected; native graph/index/dependants/text/overrides and disk unchanged')

      var saved = ContactQuests.createQuest(level, code(chapter), prefix+'saved-description', {title:'Graph saved description',locale:'en_us'})
      var global = ContactQuests.createQuest(level, code(chapter), prefix+'global-description', {title:'Graph global description',locale:'en_us'})
      currentCase = 'kubejs.graph_options.description_saved_clear'
      for (var locale of Assertions.locales()) {
        for (var quest of [saved, global]) {
          check(ContactQuests.editQuestDescription(code(quest), ['Native first line','Native second line'], String(locale)), 'Native description seed failed')
          check(ContactQuests.editQuestTitleSaved(level,code(quest),'Graph title sentinel',String(locale)), 'Unrelated title override seed failed')
          check(ContactQuests.editQuestDescriptionSaved(level,code(quest),['Saved first line','Saved second line'],String(locale)), 'Nonempty saved description seed failed')
        }
      }
      Assertions.verifyDescriptions(player, 'seeded')
      for (var locale of Assertions.locales())
        check(ContactQuests.editQuestDescriptionSaved(level,code(saved),[],String(locale)), 'Saved empty array rejected')
      Assertions.verifyDescriptions(player, 'saved')
      Results.recordCase(currentCase, true, 'Saved [] hides nonempty native description with an explicit empty override; native text and unrelated title override preserved')
      currentCase = 'kubejs.graph_options.description_global_clear'
      for (var locale of Assertions.locales())
        check(ContactQuests.editQuestDescription(code(global),[],String(locale)), 'Global empty array rejected')
      Assertions.verifyDescriptions(player, 'global')
      Results.recordCase(currentCase, true, 'Global [] empties native description and removes only the description locale overrides; title sentinel preserved')
      currentCase = 'kubejs.graph_options.persisted'
      Assertions.verifyAndCapture(player)
    } catch (error) {
      Results.recordCase(currentCase, false, String(error))
      Results.recordCase('kubejs.graph_options.failure', false, currentCase+': '+error)
      console.error('[contactquests_graph_options_test] '+currentCase+': '+error)
    }
  })
})()
