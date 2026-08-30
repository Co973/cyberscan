package com.cyberscan.app.core.di

import android.content.Context
import com.cyberscan.app.core.sensors.EmfSensorManager
import com.cyberscan.app.core.shell.AppProcessRegistry
import com.cyberscan.app.core.shell.CommandExecutor
import com.cyberscan.app.core.shell.LoopingShellProcess
import com.cyberscan.app.core.shell.ShellExecutor
import com.cyberscan.app.data.bluetooth.BluetoothRepository
import com.cyberscan.app.data.network.NetworkRepository
import com.cyberscan.app.service.BluetoothAdapterGateway
import com.cyberscan.app.service.ScanSessionController
import com.cyberscan.app.service.RootBluetoothAdapterGateway
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideProcessRegistry(): AppProcessRegistry = AppProcessRegistry()

    @Provides
    @Singleton
    fun provideShellExecutor(): ShellExecutor = ShellExecutor()

    @Provides
    fun provideCommandExecutor(shellExecutor: ShellExecutor): CommandExecutor = shellExecutor

    @Provides
    @Singleton
    fun provideLoopingShellProcess(registry: AppProcessRegistry): LoopingShellProcess =
        LoopingShellProcess(tag = "bluelog-scan", processRegistry = registry)

    @Provides
    @Singleton
    fun provideBluetoothRepository(loop: LoopingShellProcess): BluetoothRepository =
        BluetoothRepository(loop)

    @Provides
    @Singleton
    fun provideNetworkRepository(executor: CommandExecutor): NetworkRepository =
        NetworkRepository(executor)

    @Provides
    @Singleton
    fun provideAdapterGateway(executor: CommandExecutor): BluetoothAdapterGateway =
        RootBluetoothAdapterGateway(executor)

    @Provides
    @Singleton
    fun provideEmfSensorManager(@ApplicationContext context: Context): EmfSensorManager =
        EmfSensorManager(context)

    @Provides
    @Singleton
    fun provideScanController(
        executor: CommandExecutor,
        adapterGateway: BluetoothAdapterGateway,
        bluetooth: BluetoothRepository,
        network: NetworkRepository,
        emf: EmfSensorManager,
    ): ScanSessionController = ScanSessionController(
        rootExecutor = executor,
        adapterGateway = adapterGateway,
        bluetooth = bluetooth,
        network = network,
        emf = emf,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )
}

