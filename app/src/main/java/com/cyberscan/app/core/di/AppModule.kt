package com.cyberscan.app.core.di

import android.content.Context
import com.cyberscan.app.core.sensors.EmfSensorManager
import com.cyberscan.app.core.shell.AppProcessRegistry
import com.cyberscan.app.core.shell.CommandEnvironmentResolver
import com.cyberscan.app.core.shell.CommandExecutor
import com.cyberscan.app.core.shell.LoopingShellProcess
import com.cyberscan.app.core.shell.ShellCommandCapabilityProbe
import com.cyberscan.app.core.shell.ShellExecutor
import com.cyberscan.app.data.bluetooth.AndroidNativeBluetoothPlatform
import com.cyberscan.app.data.bluetooth.BluelogHciBackend
import com.cyberscan.app.data.bluetooth.BluetoothDeviceAccumulator
import com.cyberscan.app.data.bluetooth.CompositeBluetoothScanner
import com.cyberscan.app.data.bluetooth.NativeBluetoothPlatform
import com.cyberscan.app.data.bluetooth.NativeBluetoothScanner
import com.cyberscan.app.data.network.NetworkRepository
import com.cyberscan.app.service.AndroidScanServiceLauncher
import com.cyberscan.app.service.BluetoothScanGateway
import com.cyberscan.app.service.ScanController
import com.cyberscan.app.service.ScanServiceLauncher
import com.cyberscan.app.service.ScanSessionController
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
    fun provideCommandEnvironmentResolver(executor: CommandExecutor): CommandEnvironmentResolver =
        CommandEnvironmentResolver(ShellCommandCapabilityProbe(executor))

    @Provides
    @Singleton
    fun provideLoopingShellProcess(registry: AppProcessRegistry): LoopingShellProcess =
        LoopingShellProcess(tag = "bluelog-scan", processRegistry = registry)

    @Provides
    @Singleton
    fun provideBluetoothAccumulator(): BluetoothDeviceAccumulator = BluetoothDeviceAccumulator()

    @Provides
    @Singleton
    fun provideNativeBluetoothPlatform(
        @ApplicationContext context: Context,
    ): NativeBluetoothPlatform = AndroidNativeBluetoothPlatform(context)

    @Provides
    @Singleton
    fun provideNativeBluetoothScanner(
        platform: NativeBluetoothPlatform,
        accumulator: BluetoothDeviceAccumulator,
    ): NativeBluetoothScanner = NativeBluetoothScanner(platform, accumulator)

    @Provides
    @Singleton
    fun provideBluelogHciBackend(
        resolver: CommandEnvironmentResolver,
        executor: CommandExecutor,
        loop: LoopingShellProcess,
    ): BluelogHciBackend = BluelogHciBackend(resolver, executor, loop)

    @Provides
    @Singleton
    fun provideCompositeBluetoothScanner(
        native: NativeBluetoothScanner,
        external: BluelogHciBackend,
        accumulator: BluetoothDeviceAccumulator,
    ): CompositeBluetoothScanner = CompositeBluetoothScanner(native, external, accumulator)

    @Provides
    fun provideBluetoothScanGateway(scanner: CompositeBluetoothScanner): BluetoothScanGateway = scanner

    @Provides
    @Singleton
    fun provideNetworkRepository(
        executor: CommandExecutor,
        resolver: CommandEnvironmentResolver,
    ): NetworkRepository = NetworkRepository(executor, resolver)

    @Provides
    @Singleton
    fun provideEmfSensorManager(@ApplicationContext context: Context): EmfSensorManager =
        EmfSensorManager(context)

    @Provides
    @Singleton
    fun provideScanServiceLauncher(@ApplicationContext context: Context): ScanServiceLauncher =
        AndroidScanServiceLauncher(context)

    @Provides
    @Singleton
    fun provideScanSessionController(
        bluetooth: BluetoothScanGateway,
        network: NetworkRepository,
        emf: EmfSensorManager,
    ): ScanSessionController = ScanSessionController(
        bluetooth = bluetooth,
        network = network,
        emf = emf,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    @Provides
    fun provideScanController(controller: ScanSessionController): ScanController = controller
}
