#include <sys/system_properties.h>
#include <cstring>
#include <mutex>
#include <string>
#include "./xdl.h"
#include <android/log.h>
#include <dlfcn.h>
#include "Dobby/dobby.h"
#include "VirtualSpoof.h"


#define LOG_TAG "VirtualSpoof"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

static int (*orig_system_property_get)(const char *name, char *value) = nullptr;
static std::mutex g_spoof_mutex;
static std::string g_manufacturer = "Google";
static std::string g_brand = "google";
static std::string g_model = "Pixel 6";
static std::string g_device = "oriole";
static std::string g_product = "oriole";
static std::string g_fingerprint = "google/oriole/oriole:12/SP1A.210812.015/7679548:user/release-keys";
static std::string g_serial = "1A2B3C4D5E6F";

static int copy_spoofed_value(const std::string &source, char *value) {
    strcpy(value, source.c_str());
    return static_cast<int>(source.size());
}

void setDeviceSpoofValues(
        const char *manufacturer,
        const char *brand,
        const char *model,
        const char *device,
        const char *product,
        const char *fingerprint,
        const char *serial) {
    std::lock_guard<std::mutex> lock(g_spoof_mutex);
    if (manufacturer != nullptr) g_manufacturer = manufacturer;
    if (brand != nullptr) g_brand = brand;
    if (model != nullptr) g_model = model;
    if (device != nullptr) g_device = device;
    if (product != nullptr) g_product = product;
    if (fingerprint != nullptr) g_fingerprint = fingerprint;
    if (serial != nullptr) g_serial = serial;
    LOGD("[spoof] Updated profile: %s %s", g_manufacturer.c_str(), g_model.c_str());
}


int my_system_property_get(const char *name, char *value) {
    std::lock_guard<std::mutex> lock(g_spoof_mutex);
    if (strcmp(name, "ro.product.model") == 0) {
        return copy_spoofed_value(g_model, value);
    }
    if (strcmp(name, "ro.product.brand") == 0) {
        return copy_spoofed_value(g_brand, value);
    }
    if (strcmp(name, "ro.product.manufacturer") == 0) {
        return copy_spoofed_value(g_manufacturer, value);
    }
    if (strcmp(name, "ro.product.device") == 0) {
        return copy_spoofed_value(g_device, value);
    }
    if (strcmp(name, "ro.product.name") == 0) {
        return copy_spoofed_value(g_product, value);
    }
    if (strcmp(name, "ro.build.fingerprint") == 0) {
        return copy_spoofed_value(g_fingerprint, value);
    }
    if (strcmp(name, "ro.serialno") == 0) {
        return copy_spoofed_value(g_serial, value);
    }
    if (orig_system_property_get) {
        return orig_system_property_get(name, value);
    }
    value[0] = '\0';
    return 0;
}

void install_property_get_hook() {
    void* handle = xdl_open("libc.so", XDL_DEFAULT);
    void* target = xdl_dsym(handle, "__system_property_get", nullptr);
    if (target) {
        if (DobbyHook(target, (void*)my_system_property_get, (void**)&orig_system_property_get) == 0) {
            LOGD("Spoof installed successfully");
        } else {
            LOGD("Spoof hook failed");
        }
        xdl_close(handle);
    } else{
        xdl_close(handle);
    }

}


__attribute__((constructor)) void init_virtual_spoof()
{
    install_property_get_hook();
    LOGD("VirtualSpoof: __system_property_get hook loaded");
}
