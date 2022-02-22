package com.example.thesisproject

import java.util.Comparator

class Marzullo {
    class Range(val start : Long, val end : Long){}

    companion object {
        class Tuple(val offset: Long, val type: Int){}
        fun apply(ranges : ArrayList<Range>): Range{
            val table = ArrayList<Tuple>()
            for(range in ranges){
                //val c = (range.start.toDouble() + range.end)/2
                //val r = (range.end - range.start)/2
                val tuple1 = Tuple(range.start, +1)
                val tuple2 = Tuple(range.end, -1)
                table.add(tuple1)
                table.add(tuple2)
            }
            table.sortBy { t -> t.offset }

            var count = 0
            var max = 0
            var answer = Range(0,0)
            for(i in 0 until table.size-1){
                count += table[i].type
                if(count > max){
                    max = count
                    answer = Range(table[i].offset, table[i+1].offset)
                }
            }
            return answer
        }
    }
}