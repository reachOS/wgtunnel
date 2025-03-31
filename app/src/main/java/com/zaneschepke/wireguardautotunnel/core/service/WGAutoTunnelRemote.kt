package com.zaneschepke.wireguardautotunnel.core.service

import android.content.Intent
import android.net.VpnService
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.zaneschepke.wireguardautotunnel.IWGAutoTunnel
import com.zaneschepke.wireguardautotunnel.MainActivity
import com.zaneschepke.wireguardautotunnel.core.tunnel.TunnelManager
import com.zaneschepke.wireguardautotunnel.domain.entity.AppSettings
import com.zaneschepke.wireguardautotunnel.domain.entity.TunnelConf
import com.zaneschepke.wireguardautotunnel.domain.repository.AppDataRepository
import com.zaneschepke.wireguardautotunnel.util.extensions.withData
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class WGAutoTunnelRemote : LifecycleService() {

	@Inject
	lateinit var appDataRepository: AppDataRepository

	@Inject
	lateinit var tunnelManager: TunnelManager

	@Inject
	lateinit var serviceManager: ServiceManager

	lateinit var appSettings: StateFlow<AppSettings?>

	lateinit var tunnels: StateFlow<List<TunnelConf>?>

	fun saveAppSettings(appSettings: AppSettings) = lifecycleScope.launch {
		appDataRepository.settings.save(appSettings)
	}

	fun saveTunnel(tunnelConf: TunnelConf) = lifecycleScope.launch {
		appDataRepository.tunnels.save(tunnelConf)
	}

	override fun onCreate() {
		super.onCreate()
		appSettings = appDataRepository.settings.flow.stateIn(
			scope = lifecycleScope,
			started = SharingStarted.Companion.WhileSubscribed(5000),
			initialValue = null,
		)
		tunnels = appDataRepository.tunnels.flow.stateIn(
			scope = lifecycleScope,
			started = SharingStarted.Companion.WhileSubscribed(5000),
			initialValue = null,
		)
	}

	private val binder = object : IWGAutoTunnel.Stub() {
		override fun saveTunnel(name: String, config: String, callback: IWGAutoTunnel.ICallback) {
			try {
				val amConfig = TunnelConf.configFromAmQuick(config)
				val tunnelConf = TunnelConf.tunnelConfigFromAmConfig(amConfig, name)
				saveTunnel(tunnelConf)
				callback.onSuccess()
			} catch (e: Exception) {
				Timber.e(e)
				callback.onError(e.message)
			}
		}

		override fun deleteTunnel(name: String, callback: IWGAutoTunnel.ICallback) {
			lifecycleScope.launch {
				try {
					val tunnel = appDataRepository.tunnels.findByTunnelName(name)
					if (tunnel != null) {
						appDataRepository.tunnels.delete(tunnel)
					}
					callback.onSuccess()
				} catch (e: Exception) {
					Timber.e(e)
					callback.onError(e.message)
				}
			}
		}

		override fun setAsPrimaryTunnel(name: String, callback: IWGAutoTunnel.ICallback) {
			lifecycleScope.launch {
				try {
					val tunnelConf = appDataRepository.tunnels.findByTunnelName(name)
					if (tunnelConf != null) {
						appDataRepository.tunnels.updatePrimaryTunnel(
							when (tunnelConf.isPrimaryTunnel) {
								true -> null
								false -> tunnelConf
							},
						)
						callback.onSuccess()
					} else {
						callback.onError("No such tunnel: $name")
					}
				} catch (e: Exception) {
					Timber.e(e)
					callback.onError(e.message)
				}
			}
		}

		override fun setTunnel(name: String, enabled: Boolean, callback: IWGAutoTunnel.ICallback) {
			lifecycleScope.launch {
				try {
					val vpnIntent = VpnService.prepare(baseContext)
					Timber.d("VPN prepare intent: $vpnIntent")
					if (vpnIntent != null) {
						startActivity(
							Intent(baseContext, MainActivity::class.java).apply {
								putExtra("prepare", true)
								flags = Intent.FLAG_ACTIVITY_NEW_TASK
							}
						)
					}
					val tunnelConf = appDataRepository.tunnels.findByTunnelName(name)
					if (tunnelConf != null) {
						if (enabled) {
							appSettings.withData {
								tunnelManager.startTunnel(tunnelConf)
							}
						} else {
							appSettings.withData {
								tunnelManager.stopTunnel(tunnelConf)
							}
						}
						callback.onSuccess()
					} else {
						callback.onError("No such tunnel: $name")
					}
				} catch (e: Exception) {
					Timber.e(e)
					callback.onError(e.message)
				}
			}
		}

		override fun setAutoTunnel(enabled: Boolean, callback: IWGAutoTunnel.ICallback) {
			try {
				if (enabled) {
					serviceManager.startAutoTunnel(false)
				} else {
					serviceManager.stopAutoTunnel()
				}
				callback.onSuccess()
			} catch (e: Exception) {
				Timber.e(e)
				callback.onError(e.message)
			}
		}

		override fun tunnelOnMobileData(enabled: Boolean, callback: IWGAutoTunnel.ICallback) {
			lifecycleScope.launch {
				try {
					appSettings.withData {
						saveAppSettings(
							it.copy(
								isTunnelOnMobileDataEnabled = enabled,
							),
						)
					}
					callback.onSuccess()
				} catch (e: Exception) {
					Timber.e(e)
					callback.onError(e.message)
				}
			}
		}

		override fun tunnelOnUntrustedWifi(enabled: Boolean, callback: IWGAutoTunnel.ICallback) {
			lifecycleScope.launch {
				try {
					appSettings.withData {
						saveAppSettings(
							it.copy(
								isTunnelOnWifiEnabled = enabled,
							),
						)
					}
					callback.onSuccess()
				} catch (e: Exception) {
					Timber.e(e)
					callback.onError(e.message)
				}
			}
		}

		override fun tunnelOnEthernet(enabled: Boolean, callback: IWGAutoTunnel.ICallback) {
			lifecycleScope.launch {
				try {
					appSettings.withData {
						saveAppSettings(
							it.copy(
								isTunnelOnEthernetEnabled = enabled,
							),
						)
					}
					callback.onSuccess()
				} catch (e: Exception) {
					Timber.e(e)
					callback.onError(e.message)
				}
			}
		}

		override fun stopOnNoInternet(enabled: Boolean, callback: IWGAutoTunnel.ICallback) {
			lifecycleScope.launch {
				try {
					appSettings.withData {
						saveAppSettings(
							it.copy(isStopOnNoInternetEnabled = enabled),
						)
					}
					callback.onSuccess()
				} catch (e: Exception) {
					Timber.e(e)
					callback.onError(e.message)
				}
			}
		}

		override fun startTunnelOnBoot(enabled: Boolean, callback: IWGAutoTunnel.ICallback) {
			lifecycleScope.launch {
				try {
					appSettings.withData {
						saveAppSettings(
							it.copy(
								isRestoreOnBootEnabled = enabled,
							)
						)
					}
					callback.onSuccess()
				} catch (e: Exception) {
					Timber.e(e)
					callback.onError(e.message)
				}
			}
		}

		override fun getPrimaryTunnelName(callback: IWGAutoTunnel.ITunnelNameCallback) {
			lifecycleScope.launch {
				val tunnel = appDataRepository.tunnels.findPrimary().firstOrNull()
				callback.onName(tunnel?.name)
			}
		}

	}

	override fun onBind(intent: Intent): IBinder {
		super.onBind(intent)
		return binder
	}
}
