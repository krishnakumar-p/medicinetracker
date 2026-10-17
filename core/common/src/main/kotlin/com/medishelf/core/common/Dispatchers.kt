package com.medishelf.core.common

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val dispatcher: MediShelfDispatchers)

enum class MediShelfDispatchers { DEFAULT, IO }

/** Process-lifetime scope for work that must outlive any screen. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
