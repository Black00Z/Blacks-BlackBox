package com.onebitmonochrome.blacksbbox.bean


data class GmsBean(
	val userID: Int,
	val userName: String,
	var isInstalledGms: Boolean,
	var hasGmsTraces: Boolean
)


data class GmsInstallBean(
	val userID: Int,
	val success: Boolean,
	val msg: String,
	val isInstalledGms: Boolean,
	val hasGmsTraces: Boolean
)