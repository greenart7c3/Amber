package com.greenart7c3.nostrsigner.tools.detekt

import dev.detekt.api.RuleName
import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

/** The "amber" rule set; detekt finds it through META-INF/services. */
@Suppress("unused")
class AmberRuleSetProvider : RuleSetProvider {
    override val ruleSetId = RuleSetId("amber")

    override fun instance() = RuleSet(ruleSetId, mapOf(RuleName("NoComments") to ::NoComments))
}
