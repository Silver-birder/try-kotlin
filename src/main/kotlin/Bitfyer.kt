package main.kotlin

import org.apache.beam.sdk.Pipeline
import org.apache.beam.sdk.io.TextIO
import org.apache.beam.sdk.metrics.Metrics
import org.apache.beam.sdk.options.Default
import org.apache.beam.sdk.options.PipelineOptions
import org.apache.beam.sdk.options.PipelineOptionsFactory
import org.apache.beam.sdk.transforms.*
import org.apache.beam.sdk.values.KV
import org.apache.beam.sdk.values.PCollection

object Bitfyer {
    @JvmStatic // objectのmethodをstaticにcallしたい場合、JvmStaticをつけるらしい
    fun main(args: Array<String>) {
        // PipelineOptionsFactoryでは、fromArgs(*args) で入力値を入れ、withValidationでチェック、create()するのが基本。
        // ただ、後続で型を指定したいので、`as`を使っている。(options.inputFile とか)
        // asは、castの意味。バッククォートは、methodを実行できるようにしている。asは予約語なので使えないが、methpdとして使いたいって感じ。
        // ::class.javaは、クラスオブジェクトを取得する文法。
        val options = PipelineOptionsFactory.fromArgs(*args).withValidation().`as`(BitfyerOptions::class.java)
        val p = Pipeline.create(options)

        // textをread
        p.apply("ReadLines", TextIO.read().from(options.inputFile))
            .apply(FilterBit())
            .apply(MapElements.via(FormatAsTextFn()))
            .apply("WriteCounts", TextIO.write().to(options.output))
        p.run().waitUntilFinish()
    }

    internal class ExtractWordsFn : DoFn<String?, String?>() {
        private val emptyLines = Metrics.counter(
            ExtractWordsFn::class.java, "emptyLines"
        )

        @ProcessElement
        fun processElement(c: ProcessContext) {
            if (c.element()!!.trim { it <= ' ' }.isEmpty()) {
                emptyLines.inc()
            }
            val words = c.element()!!.split("[^\\p{L}]+".toRegex()).toTypedArray()
            for (word in words) {
                if (!word.isEmpty()) {
                    c.output(word)
                }
            }
        }
    }

    internal class Extract: DoFn<String, KV<String, Int>>() {
        @ProcessElement
        fun processElement(c: ProcessContext) {
            val el = c.element().split(',')
            // BTC/JPY,bitflyer,1519845731987,1127174.0,1126166.0
            val com = el[1]
            val up = el[3].toDouble().toInt()
            c.output(KV.of(com, up))
        }
    }

    class FormatAsTextFn : SimpleFunction<KV<String, MutableIterable<Int>>, String>() {
        override fun apply(input: KV<String, MutableIterable<Int>>): String {
            return input.key + input.value.toString()
        }
    }

    class FilterBit: PTransform<PCollection<String>, PCollection<KV<String, MutableIterable<Int>>>>() {
        override fun expand(input: PCollection<String>): PCollection<KV<String, MutableIterable<Int>>>? {
            return input
                .apply(ParDo.of<String, KV<String, Int>>(Extract()))
                .apply(GroupByKey.create<String, Int>())
        }
    }

    // PTransformというIFを実装
    class CountWords :
        // https://beam.apache.org/releases/javadoc/2.2.0/org/apache/beam/sdk/transforms/PTransform.html
        // input, output の形式。
        PTransform<PCollection<String?>, PCollection<KV<String, Long>>>() {
        // PTransformは、expandを実装する必要がある
        // https://beam.apache.org/releases/javadoc/2.2.0/org/apache/beam/sdk/transforms/PTransform.html#expand-InputT-
        override fun expand(lines: PCollection<String?>): PCollection<KV<String, Long>> {

            // ParDo. Transformの中で基本的な処理？. (PTransformの中に、ParDo, GroupByKey, などある)
            // ParDoは、DoFn を継承したものじゃないとだめ。
            val words =
                lines.apply(
                    ParDo.of<String, String>(ExtractWordsFn())
                )
            // GroupByKeyは、キーとなるものでGroupByする
            // Combineは、...
            // https://beam.apache.org/documentation/transforms/java/aggregation/combine/ を読もう
            // https://github.com/apache/beam/tree/master/examples/java/src/main/java/org/apache/beam/examples も読もう・
            // CoGroupByKey ...
            // Flatten ...
            // Partition ...
            return words.apply(Count.perElement())
        }
    }

    interface BitfyerOptions : PipelineOptions {
        @get:Default.String("./src/main/kotlin/bitfyer.txt")
        var inputFile: String?

        @get:Default.String("./src/main/kotlin/bitfyer_out.txt")
        var output: String?
    }
}