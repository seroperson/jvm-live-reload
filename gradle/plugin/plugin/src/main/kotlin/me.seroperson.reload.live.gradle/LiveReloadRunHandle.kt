package me.seroperson.reload.live.gradle

import me.seroperson.reload.live.runner.CompileResult
import me.seroperson.reload.live.runner.DevServer
import me.seroperson.reload.live.runner.DevServerRunner
import me.seroperson.reload.live.settings.DevServerSettings
import org.gradle.deployment.internal.Deployment
import org.gradle.deployment.internal.DeploymentHandle
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.inject.Inject

open class LiveReloadRunHandle
@Inject
constructor(
    private val params: LiveReloadRunParams,
) : DeploymentHandle {
    private var deployment: Deployment? = null
    private var devServer: DevServer? = null
    private val lock: ReadWriteLock = ReentrantReadWriteLock()

    private val isChanged: Boolean
        get() {
            lock.readLock().lock()
            try {
                return deployment!!.status().hasChanged()
            } finally {
                lock.readLock().unlock()
            }
        }

    override fun isRunning(): Boolean {
        lock.readLock().lock()
        try {
            return devServer != null
        } finally {
            lock.readLock().unlock()
        }
    }

    private fun reloadCompile(): CompileResult {
        lock.readLock().lock()
        try {
            val failure = deployment!!.status().failure
            if (failure != null) {
                return CompileResult.CompileFailure(
                    RuntimeException("Gradle Build Failure " + failure.message, failure),
                )
            }
            return CompileResult.CompileSuccess(params.applicationClasspath.toList())
        } catch (e: Exception) {
            logger.error(e.message, e)
            return CompileResult.CompileFailure(RuntimeException("Gradle Build Failure " + e.message, e))
        } finally {
            lock.readLock().unlock()
        }
    }

    override fun start(deployment: Deployment) {
        lock.writeLock().lock()
        this.deployment = deployment
        try {
            devServer =
                DevServerRunner
                    .getInstance()
                    .run(
                        DevServerSettings(listOf(), listOf(), params.settings),
                        params.dependencyClasspath.toList(),
                        this::reloadCompile,
                        this::isChanged,
                        listOf(),
                        null,
                        "me.seroperson.reload.live.webserver.DevServerStart",
                        params.mainClass,
                        lock,
                        params.startupHooks,
                        params.shutdownHooks,
                        LiveReloadLogger(),
                    )
        } finally {
            lock.writeLock().unlock()
        }
    }

    override fun stop() {
        lock.writeLock().lock()
        logger.info("Application is stopping ...")
        try {
            devServer?.close()
            logger.info("Application stopped.")
        } catch (e: Exception) {
            logger.error("Application stopped with exception", e)
        } finally {
            devServer = null
            deployment = null
            lock.writeLock().unlock()
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(LiveReloadRunHandle::class.java)
    }
}
