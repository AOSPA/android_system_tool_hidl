/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package writer.files

import parser.LOG_NAME
import parser.config
import parser.files.AbstractFileParser
import parser.files.InterfaceFileParser
import writer.getDescSummaryText
import writer.getOutPath
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

data class EntryData(val fullName: String, //package.BaseName
                     val baseName: String,
                     val packageName: String,
                     val packageVersion: Float,
                     val summary: String,
                     val relPath: Path)

class IndexFileWriter : AbstractFileWriter() {

    override val baseName = "index"
    override val templateResource = "template/${this.baseName}.html"
    override val path: Path by lazy { Paths.get("${config.outDir}${File.separator}${this.baseName}.html") }

    private val entries = mutableListOf<EntryData>()

    fun addEntry(parser: AbstractFileParser) {
        val summaryStr = when (parser) {
            is InterfaceFileParser -> getDescSummaryText(parser.description)
            else -> ""
        }
        entries.add(EntryData(
                fullName = "${parser.packageName}.${parser.name}",
                baseName = parser.name,
                packageName = parser.packageName,
                packageVersion = parser.packageVersion,
                summary = summaryStr,
                relPath = config.outDir.relativize(getOutPath(parser, config.outDir))
        ))
    }

    override fun printInfo() {
        super.printInfo()
        println( "IndexFileWriter:")
        println("  entries: ${this.entries.size}")
    }

    /*
     * HTML index file
     */

    override fun replaceVars() {
        replaceVar("title", "Index")

        replaceVar("entries") {
            val sb = StringBuilder()
            if (entries.isNotEmpty()) {
                entries.sortWith(EntryNameComparator())
                sb.append(buildEntryTable())
            }
            sb.toString()
        }
    }

    private fun buildEntryTable(): String {
        return """
<table>
  <tr>
    <th>Entry</th>
    <th>Version</th>
    <th>Summary</th>
  </tr>
${entries.map { buildClassEntry(it) }.joinToString("\n")}
</table>
""".trim()
    }

    private fun buildClassEntry(entry: EntryData): String {
        return """
<tr>
  <td>
<a href="${entry.relPath}">${entry.fullName}</a>
  </td>
  <td>
${entry.packageVersion}
  </td>
  <td>
${entry.summary}
  </td>
</tr>
""".trim()
    }

    private class EntryNameComparator : Comparator<EntryData> {
        override fun compare(entry1: EntryData, entry2: EntryData): Int {
            return if (entry1.fullName != entry2.fullName) {
                //sort on name first, alphabetic
                when {
                    entry1.fullName < entry2.fullName -> -1
                    entry1.fullName > entry2.fullName -> 1
                    else -> 0
                }
            } else {
                //if same name, reverse sort on pkg version (highest first)
                when {
                    entry1.packageVersion < entry2.packageVersion -> 1
                    entry1.packageVersion > entry2.packageVersion -> -1
                    else -> 0
                }
            }
        }
    }

    /*
     * YAML toc file
     */

    private val tocFileName = "_book.yaml"
    private val tocFileRelPath = "/reference/hidl"
    private val tocOutPath: Path by lazy { Paths.get("${config.outDir}${File.separator}${this.tocFileName}") }

    //write toc yaml file after html index
    override fun onWrite() {
        super.onWrite()
        if (!config.lintMode) {
            val fp = tocOutPath.toFile()
            fp.bufferedWriter().use {
                it.write(buildTocHeader())
                it.write(buildTocEntries(collectPackages()))
            }
            if (!fp.isFile) throw FileSystemException(fp, reason = "Error writing file")
            println("$LOG_NAME Wrote toc: $tocOutPath")
        }
    }

    private fun buildTocHeader(): String {
        return """
# Generated by hidl-doc
toc:
- title: Index
  path: $tocFileRelPath
""".trimStart()
    }

    private fun buildTocEntries(pkgEntries: Map<String, List<EntryData>>): String {
        val sb = StringBuilder()

        for ((pkgName, entryList) in pkgEntries) {
            sb.append("- title: $pkgName\n  section:\n")

            entryList.forEach { entry ->
                sb.append("  - title: ${entry.baseName} @${entry.packageVersion}\n")
                sb.append("    path: ${tocFileRelPath}/${entry.relPath}\n")
            }
        }
        return sb.toString()
    }

    private fun collectPackages(): Map<String, List<EntryData>> {
        val pkgEntries = mutableMapOf<String, MutableList<EntryData>>()

        this.entries.forEach { entry ->
            if (pkgEntries.containsKey(entry.packageName)) {
                pkgEntries[entry.packageName]!!.add(entry)
            } else {
                pkgEntries[entry.packageName] = mutableListOf(entry)
            }
        }
        //sort on package name. entries *should* already be sorted
        return pkgEntries.toSortedMap()
    }
}