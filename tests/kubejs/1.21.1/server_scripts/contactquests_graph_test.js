// This exercises the public binding from Rhino, including JS object -> NBT,
// precise hex IDs, persistent graph edits, native dependencies and deletion.
(function () {
  var Results = Java.loadClass('com.creeperyang.contactquests.test.ContactQuestsTestServerController')
  var Assertions = Java.loadClass('com.creeperyang.contactquests.test.ContactQuestsScriptAssertions')
  var ran = false
  function check(value, message) { if (!value) throw new Error(message) }
  ServerEvents.tick(event => {
    if (Java.loadClass('java.lang.Boolean').getBoolean('contactquests.test.rejoin')) return
    if (ran || !Results.isCasePassed('kubejs.npc.no_task.immediate')) return
    ran = true
    var level = event.server.overworld()
    try {
      var chapter = ContactQuests.createChapter(level, 'cqtest:graph/main', 'Script chapter')
      var chapterId = String(chapter.codeString)
      var second = ContactQuests.createChapter(level, 'cqtest:graph/second', 'Second chapter')
      var quest = ContactQuests.createQuest(level, chapterId, 'cqtest:graph/main_quest', 'Script quest')
      var id = String(quest.codeString)
      var dependency = ContactQuests.createQuest(level, chapterId, 'cqtest:graph/dependency', 'Dependency')
      var depId = String(dependency.codeString)
      check(String(ContactQuests.createChapter(level, 'cqtest:graph/main', 'ignored').codeString) === chapterId, 'Chapter key was not idempotent')
      check(String(ContactQuests.createQuest(level, chapterId, 'cqtest:graph/main_quest', 'ignored').codeString) === id, 'Quest key was not idempotent')
      check(String(ContactQuests.getQuest(level, id).rawTitle) === 'Script quest', 'Idempotent create replaced edited title')
      check(ContactQuests.listChapters(level).contains(chapter) && ContactQuests.listQuests(level, chapterId).contains(quest), 'Graph lists omitted created objects')
      Results.recordCase('kubejs.graph.create_query', true, 'Stable keys reused chapters/quests and actual FTB IDs/list membership verified')

      check(ContactQuests.editQuestTitleSaved(level, id, 'old saved title', 'en_us'), 'Could not prepare existing text override')

      check(ContactQuests.updateChapter(level, chapterId, {title: 'Edited chapter', subtitle: ['Chapter subtitle'],
        defaultQuestShape: 'circle', defaultQuestSize: 1.5, progressionMode: 'LINEAR', hideQuestUntilDepsComplete: true}), 'Chapter update returned false')
      check(ContactQuests.updateQuest(level, id, {title: 'Edited quest', subtitle: 'Edited subtitle', description: ['Edited description'],
        x: 2, y: 3, requiredTags: ['GraphTagA', 'GraphTagB'], tagMode: 'ALL', hideWithoutTags: true,
        mutex: [depId], mutexMode: 'NUMBER', mutexCount: 1, hideIfLocked: true, dependencies: [depId]}), 'Quest update returned false')
      check(Assertions.graphRules(level, id, depId), 'Actual FTB rules/text/NBT do not match the JS update')
      var rejected = false
      try { ContactQuests.updateQuest(level, depId, {title: 'must not change', dependencies: [id]}) } catch (expected) { rejected = true }
      check(rejected && String(dependency.rawTitle) === 'Dependency', 'Dependency cycle changed graph before validation')
      rejected = false
      try { ContactQuests.updateQuest(level, id, {title: 'must not change', mutexCount: 0}) } catch (expected) { rejected = true }
      check(rejected && String(quest.rawTitle) === 'Edited quest', 'Invalid threshold partially changed graph')
      Results.recordCase('kubejs.graph.edit_rules', true, 'Chapter defaults, quest text, dependencies, tags, mutex and atomic invalid/cycle rejection verified')

      var task = ContactQuests.addTask(level, id, 'cqtest:graph/task', 'contactquests:parcel_task',
        {item: 'minecraft:diamond', count: 4, TargetAddressee: 'CQGraphTask'})
      var taskId = String(task.codeString)
      check(ContactQuests.listTasks(level, id).size() === 1 && String(ContactQuests.getTask(level, taskId).codeString) === taskId, 'Task queries failed')
      check(ContactQuests.editParcelTaskSaved(level, taskId, Item.of('minecraft:diamond'), 99, 'CQGraphStale'), 'Could not prepare old task override')
      check(ContactQuests.updateTask(level, taskId, {count: 6, TargetAddressee: 'CQGraphEdited'}), 'Task update failed')
      var reward = ContactQuests.addReward(level, id, 'cqtest:graph/reward', 'contactquests:parcel',
        {item: {id: 'minecraft:apple', count: 1}, count: 2, target_addressee: 'CQGraphReward', unlock_tags: ['GraphRewardTag']})
      var rewardId = String(reward.codeString)
      check(ContactQuests.listRewards(level, id).size() === 1 && ContactQuests.getReward(level, rewardId) !== null, 'Reward queries failed')
      check(ContactQuests.editParcelRewardSaved(level, rewardId, Item.of('minecraft:apple'), 99, 0, 'CQGraphReward'), 'Could not prepare old reward override')
      check(ContactQuests.updateReward(level, rewardId, {count: 3}), 'Reward update failed')
      check(Assertions.graphTaskReward(level, taskId, rewardId), 'Actual task/reward values or NPC index are stale')
      check(Assertions.graphOverridesReload(level, id, taskId, rewardId), 'Saved overrides reverted explicit graph edits')
      Results.recordCase('kubejs.graph.task_reward', true, 'JS objects converted to NBT; task/reward add/get/list/update values and indexed recipient verified')

      for (var bad of [{count: -1}, {count: 0}, {count: 1.5}]) {
        rejected = false
        try { ContactQuests.updateTask(level, taskId, bad) } catch (expected) { rejected = true }
        check(rejected && Number(task.count) === 6, 'Invalid task count mutated actual task: ' + bad.count)
      }
      for (var badReward of [{count: -1}, {random_bonus: -1}, {count: 2147483647, random_bonus: 1}, {team_reward_multiplier: 0}]) {
        rejected = false
        try { ContactQuests.updateReward(level, rewardId, badReward) } catch (expected) { rejected = true }
        check(rejected && Number(reward.count) === 3, 'Invalid reward data changed actual object')
      }
      Results.recordCase('kubejs.graph.validation_and_overrides', true, 'Invalid/overflow quantities rejected atomically; earlier SavedData edits cannot revert global text/count/recipient updates after load')

      check(ContactQuests.moveQuest(level, id, String(second.codeString)), 'Quest move failed')
      check(!ContactQuests.listQuests(level, chapterId).contains(quest) && ContactQuests.listQuests(level, String(second.codeString)).contains(quest), 'Actual chapter lists did not move')
      check(ContactQuests.deleteTask(level, taskId) && ContactQuests.getTask(level, taskId) === null, 'Task deletion failed')
      check(ContactQuests.deleteReward(level, rewardId) && ContactQuests.getReward(level, rewardId) === null, 'Reward deletion failed')
      check(!ContactQuests.deleteReward(level, rewardId), 'Repeated deletion should return false')
      check(ContactQuests.deleteQuest(level, depId), 'Dependency deletion failed')
      check(Assertions.graphReferencesCleared(level, id), 'Deleted dependency/mutex reference remained')
      check(ContactQuests.deleteChapter(level, String(second.codeString)) && ContactQuests.getQuest(level, id) === null, 'Chapter did not cascade deletion')
      check(ContactQuests.deleteChapter(level, chapterId), 'Empty chapter deletion failed')
      Results.recordCase('kubejs.graph.move_delete', true, 'Quest movement, task/reward deletion, dangling references and chapter cascading deletion verified')
    } catch (error) {
      Results.recordCase('kubejs.graph.failure', false, String(error))
      console.error('[contactquests_graph_test] ' + error)
    }
  })
})()
