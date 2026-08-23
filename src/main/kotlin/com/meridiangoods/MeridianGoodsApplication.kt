package com.meridiangoods

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Entry point. Component scanning is rooted here (`com.meridiangoods`), which is what makes the
 * vertical-slice wiring conflict-free by construction: every slice lives in its own
 * `com.meridiangoods.<slicekey>` package and ships its own `@Configuration` class (a
 * [com.meridiangoods.axon.SliceModule] bean). Spring's classpath scan discovers it automatically —
 * adding a slice never means editing this file, or any other shared file. See
 * [com.meridiangoods.axon.AxonApplicationConfiguration] for how the discovered slices are folded
 * into one running Axon application.
 */
@SpringBootApplication
class MeridianGoodsApplication

fun main(args: Array<String>) {
    runApplication<MeridianGoodsApplication>(*args)
}
