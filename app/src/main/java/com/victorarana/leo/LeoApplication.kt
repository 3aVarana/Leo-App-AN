package com.victorarana.leo

import android.app.Application

/** Owns the [AppContainer] for the life of the process (iOS: the `@main App` that builds the repositories). */
class LeoApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
