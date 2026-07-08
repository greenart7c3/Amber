package com.greenart7c3.nostrsigner.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

class AmberIssueRegistry : IssueRegistry() {
    override val issues: List<Issue> = listOf(
        LongToDurationDetector.ISSUE,
    )

    override val api: Int = CURRENT_API

    override val vendor: Vendor = Vendor(
        vendorName = "Amber",
        feedbackUrl = "https://github.com/greenart7c3/Amber/issues",
    )
}
