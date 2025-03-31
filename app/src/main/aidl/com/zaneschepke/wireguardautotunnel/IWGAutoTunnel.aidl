// IWGAutoTunnel.aidl
package com.zaneschepke.wireguardautotunnel;

// Declare any non-default types here with import statements

interface IWGAutoTunnel {
	interface ICallback {
		void onSuccess();
		void onError(String error);
	}
	void saveTunnel(String name, String config, ICallback callback);
	void deleteTunnel(String name, ICallback callback);
	void setAsPrimaryTunnel(String name, ICallback callback);
	void setTunnel(String name, boolean enabled, ICallback callback);
	void setAutoTunnel(boolean enabled, ICallback callback);
	void tunnelOnMobileData(boolean enabled, ICallback callback);
	void tunnelOnUntrustedWifi(boolean enabled, ICallback callback);
	void tunnelOnEthernet(boolean enabled, ICallback callback);
	void stopOnNoInternet(boolean enabled, ICallback callback);
	void startTunnelOnBoot(boolean enabled, ICallback callback);
	interface ITunnelNameCallback {
		void onName(@nullable String name);
	}
	void getPrimaryTunnelName(ITunnelNameCallback callback);
//	List<String> getExcludedApps(String name);
//	List<String> getIncludedApps(String name);
//	void setExcludedApps(String name, in List<String> apps);
//	void setIncludedApps(String name, in List<String> apps);
}
