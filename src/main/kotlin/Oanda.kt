package main.kotlin

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.beam.sdk.Pipeline
import org.apache.beam.sdk.coders.AvroCoder
import org.apache.beam.sdk.coders.DefaultCoder
import org.apache.beam.sdk.io.TextIO
import org.apache.beam.sdk.options.Default
import org.apache.beam.sdk.options.PipelineOptions
import org.apache.beam.sdk.options.PipelineOptionsFactory
import org.apache.beam.sdk.transforms.*
import org.apache.beam.sdk.values.KV
import org.apache.beam.sdk.values.PCollection


@DefaultCoder(AvroCoder::class)
data class OandaJson(
    val instrument: String = "",
    val granularity: Granularity = Granularity.S5,
    val candles: List<Candlestick> = listOf()
)

enum class Granularity {
    S5, S10, S15, S30, M1, M2, M4, M5, M10, M15, M30, H1, H2, H3, H4, H6, H8, H12, D, W, M
}

data class Candlestick(
    val time: String = "",
    val bid: CandlestickData? = CandlestickData(o = "", h = "", l = "", c = ""),
    val ask: CandlestickData? = CandlestickData(o = "", h = "", l = "", c = ""),
    val mid: CandlestickData? = CandlestickData(o = "", h = "", l = "", c = ""),
    val volume: Int = 0,
    val complete: Boolean = false
)

data class CandlestickData(
    val o: String = "",
    val h: String = "",
    val l: String = "",
    val c: String = ""
)

object Oanda {
    @JvmStatic
    fun main(args: Array<String>) {
        val options = PipelineOptionsFactory.fromArgs(*args).withValidation().`as`(OandaOptions::class.java)
        val p = Pipeline.create(options)
        p
            .apply("ReadLines", TextIO.read().from(options.inputFile))
            .apply("Transform", Transform())
            .apply("Format", MapElements.via(Format()))
            .apply("WriteLines", TextIO.write().to(options.output))
        p.run().waitUntilFinish()
    }

    internal class Transform : PTransform<PCollection<String>, PCollection<KV<String, OandaJson>>>() {
        override fun expand(input: PCollection<String>): PCollection<KV<String, OandaJson>> {
            return input.apply(ParDo.of(JsonToData()))
        }
    }

    internal class JsonToData : DoFn<String, KV<String, OandaJson>>() {
        @ProcessElement
        fun processElement(c: ProcessContext) {
            val mapper = jacksonObjectMapper()
            val o = mapper.readValue<OandaJson>(c.element())
            c.output(KV.of(o.instrument, o))
        }
    }

    internal class Format : SimpleFunction<KV<String, OandaJson>, String>() {
        override fun apply(input: KV<String, OandaJson>): String {
            return "" + input.key
        }
    }

    interface OandaOptions : PipelineOptions {
        @get:Default.String("./src/main/kotlin/oanda.json")
        var inputFile: String?

        @get:Default.String("./src/main/kotlin/oanda_out.txt")
        var output: String?
    }
}
