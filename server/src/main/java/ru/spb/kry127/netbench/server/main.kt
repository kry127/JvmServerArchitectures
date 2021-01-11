package ru.spb.kry127.netbench.server

import org.apache.commons.cli.*

fun chooseServerArch(archDescription : String, port : Int, workers : Int) : Server {
    TODO("Implement dependency injection")
}

fun main(args : Array<String>) {
    val options = Options()

    fun buildOption(opt : String?, longOpt : String?, hasArg: Boolean, required : Boolean, description : String?) {
        val inputOption = Option(opt, longOpt, hasArg, description)
        inputOption.isRequired = required
        options.addOption(inputOption)
    }

    val arch = "arch"
    val port = "port"
    val workers = "workers"

    buildOption("a", arch, true, true, "specify architecture of server [thread|nonblock|async]")
    buildOption("p", port, true, true, "specify port for torrent client")
    buildOption("w", port, true, false, "specify number of working threads to process sorting")

    val parser: CommandLineParser = DefaultParser()
    val cmd: CommandLine

    try {
        cmd = parser.parse(options, args)

        val architecture = cmd.getOptionValue(arch)
        val serverPort = cmd.getOptionValue(port).toInt()
        val workersCount = cmd.getOptionValue(workers)?.toInt() ?: PropLoader.defaultWorkersCount
        val server = chooseServerArch(architecture, serverPort, workersCount)

        server.use {
            it.start() // start the work of the chosen server
        }

    } catch (e: ParseException) {
        println(e.message ?: "")
        val formatter = HelpFormatter()
        formatter.printHelp("netbench-jvm", options)
        System.exit(1)
    }
}
