#def InstallEnd_SetSpecificDeviceConfigs(self, *args, **kwargs):
  # Fix Scripts permissions

def FullOTA_InstallEnd(self, *args, **kwargs):
  self.script.Print("Wiping cache...")
  self.script.Mount("/cache")
  self.script.AppendExtra('delete_recursive("/cache");')
  self.script.Print("Wiping dalvik-cache...")
  self.script.Mount("/data")
  self.script.AppendExtra('delete_recursive("/data/dalvik-cache");')
  self.script.Print("Wiping battd stats...")
  self.script.AppendExtra('delete_recursive("/data/battd");')
  
  self.script.SetPermissionsRecursive("/system/etc/init.d", 0, 0, 0755, 0555, None, None)
  self.script.SetPermissionsRecursive("/system/addon.d", 0, 0, 0755, 0755, None, None)

  symlinks = []

  # libaudio link fix
  symlinks.append(("/system/lib/modules/wlan_mt6628.ko", "/system/lib/modules/wlan.ko"))
  
  self.script.MakeSymlinks(symlinks)
  self.script.ShowProgress(0.2, 0)

  self.script.Print("Finished installing KitKat for Red Rice devices, Enjoy!")

def FullOTA_DisableBootImageInstallation(self, *args, **kwargs):
  return True

def FullOTA_FormatSystemPartition(self, *args, **kwargs):
  self.script.Mount("/system")
  self.script.AppendExtra('delete_recursive("/system");')

  # returning true skips formatting /system!
  return True

def IncrementalOTA_DisableRecoveryUpdate(self, *args, **kwargs):
  return True

def IncrementalOTA_InstallEnd(self, *args, **kwargs):
  InstallEnd_SetSpecificDeviceConfigs(self, args, kwargs)
