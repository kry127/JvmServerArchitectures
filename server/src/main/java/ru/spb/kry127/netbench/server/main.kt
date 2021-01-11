package ru.spb.kry127.netbench.server

import org.apache.commons.cli.*
import ru.spb.kry127.netbench.server.PropLoader.availableArchitecturesAsString
import ru.spb.kry127.netbench.server.PropLoader.availableArchitectures

fun chooseServerArch(arch : String, port : Int, workers : Int) : Server {
    return when (arch) {
        availableArchitectures[0] -> ThreadedServer(port, workers)
        availableArchitectures[1] -> NonblockingServer(port, workers)
        availableArchitectures[2] -> AsynchronousServer(port, workers)
        else -> error("Not available architecture for implementation: $arch")
    }
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

    buildOption("a", arch, true, false,
        "specify architecture of server [$availableArchitecturesAsString]")
    buildOption("p", port, true, true,
        "specify port for server deployment")
    buildOption("w", port, true, false,
        "specify number of working threads to process sorting")

    val parser: CommandLineParser = DefaultParser()
    val cmd: CommandLine

    try {
        cmd = parser.parse(options, args)

        val architecture = cmd.getOptionValue(arch) ?: availableArchitectures.first()
        if (!(architecture in availableArchitectures)) {
            System.err.println("Specified architecture $architecture is not presented in the list.")
            System.err.println("Available architectures: [$availableArchitecturesAsString]")
            System.exit(3)
        }
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
