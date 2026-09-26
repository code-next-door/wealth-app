package io.github.codenextdoor.wealth

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** Runs on-device tests against [TestWealthApplication]. */
class WealthTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, TestWealthApplication::class.java.name, context)
}

/** The real app, but with an in-memory database and separate settings files. */
class TestWealthApplication : WealthApplication() {
    override fun createContainer() = AppContainer(this, forTests = true)
}
