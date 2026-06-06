package com.socreate.rendering

import android.content.Context
import android.opengl.GLES30.*
import java.io.BufferedReader
import java.io.InputStreamReader

object BrushShader {

    fun loadBrushProgram(context: Context): Int {
        val vertexSource = readShaderFromAssets(context, "shaders/brush_vert.glsl")
        val fragmentSource = readShaderFromAssets(context, "shaders/brush_frag.glsl")
        return createProgram(vertexSource, fragmentSource)
    }

    fun loadCompositeProgram(): Int {
        val vertexSource = """
            #version 300 es
            in vec2 aPos;
            out vec2 vTexCoord;
            void main() {
                gl_Position = vec4(aPos, 0.0, 1.0);
                vTexCoord = aPos * 0.5 + 0.5;
            }
        """.trimIndent()
        val fragmentSource = """
            #version 300 es
            precision highp float;
            uniform sampler2D uTexture;
            uniform float uOpacity;
            in vec2 vTexCoord;
            out vec4 FragColor;
            void main() {
                FragColor = texture(uTexture, vTexCoord) * uOpacity;
            }
        """.trimIndent()
        return createProgram(vertexSource, fragmentSource)
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = glCreateShader(GL_VERTEX_SHADER)
        glShaderSource(vertexShader, vertexSource)
        glCompileShader(vertexShader)

        val fragmentShader = glCreateShader(GL_FRAGMENT_SHADER)
        glShaderSource(fragmentShader, fragmentSource)
        glCompileShader(fragmentShader)

        val program = glCreateProgram()
        glAttachShader(program, vertexShader)
        glAttachShader(program, fragmentShader)
        glLinkProgram(program)

        glDeleteShader(vertexShader)
        glDeleteShader(fragmentShader)
        return program
    }

    private fun readShaderFromAssets(context: Context, path: String): String {
        return try {
            val inputStream = context.assets.open(path)
            val reader = BufferedReader(InputStreamReader(inputStream))
            reader.readLines().joinToString("\n")
        } catch (e: Exception) {
            // Fallback shaders if assets missing
            if (path.contains("vert")) {
                """
                #version 300 es
                in vec2 aPos;
                uniform vec2 uOffset;
                uniform vec2 uSize;
                void main() {
                    vec2 pos = aPos * uSize + uOffset;
                    gl_Position = vec4(pos, 0.0, 1.0);
                }
                """.trimIndent()
            } else {
                """
                #version 300 es
                precision highp float;
                uniform sampler2D uBrushTex;
                uniform vec4 uColor;
                uniform float uOpacity;
                out vec4 FragColor;
                void main() {
                    vec2 texCoord = gl_PointCoord;
                    vec4 texColor = texture(uBrushTex, texCoord);
                    FragColor = texColor * uColor;
                    FragColor.a *= uOpacity;
                }
                """.trimIndent()
            }
        }
    }
}
