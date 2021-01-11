package ru.spb.kry127.netbench.server

fun sortO2(arr : List<Int>) : List<Int> {
    val mutableLst = arr.toMutableList()

    val n = mutableLst.size
    var i = n - 1
    while (--i >= 0) {
        for (j in i..n) {
            //   i    j
            //  .
            //  .      .
            //  .     ..
            //  ..    ..
            //  ..   ...
            //  ..  ....
            //  .. .....
            if (j == n) {
                mutableLst.add(mutableLst[i])
                mutableLst.removeAt(i)
                break
            }
            if (mutableLst[j] > mutableLst[i]) {
                mutableLst.add(j, mutableLst[i])
                mutableLst.removeAt(i)
                break
            }
        }
    }

    return mutableLst
}