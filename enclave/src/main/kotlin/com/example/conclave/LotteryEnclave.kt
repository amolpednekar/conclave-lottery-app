package com.example.conclave

import com.r3.conclave.common.EnclaveCall
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.mail.EnclaveMail
import com.r3.conclave.mail.EnclaveMailId
import java.security.PublicKey
import kotlin.math.floor

class LotteryEnclave : Enclave(), EnclaveCall {

    // Keep track of lotteries
    private val lotteryBook = mutableSetOf<Int>()
    private lateinit var result: String
    lateinit var clientPublicKey: PublicKey

    override fun invoke(input: ByteArray): ByteArray? {
        val hostInput = String(input)
        require(hostInput == "DECLARE")
        result = if (lotteryBook.isNotEmpty()) {
            lotteryBook.elementAt(floor(Math.random() * lotteryBook.size).toInt()).toString()
        } else {
            "Draw not possible: No lotteries were used."
        }
        // Reply to client(s) about the result
        val replyToClient = createMail(clientPublicKey, result.toByteArray())
        postMail(replyToClient, null)
        // Tell the host too
        return result.toByteArray()
    }

    override fun receiveMail(id: EnclaveMailId, mail: EnclaveMail) {
        clientPublicKey = requireNotNull(mail.authenticatedSender) { "Public Key required" }
        val clientInput = String(mail.bodyAsBytes)
        val lotteryArgs = clientInput.split(":")
        require(lotteryArgs.size == 2) { "Lottery args are incorrect " }

        val response: String
        // Check if it client is registering a vote
        if (lotteryArgs[0] == "BUY") {
            // Require the lottery number to be 6 digit
            require(lotteryArgs[1].matches(Regex("[1234567890]{6}")))

            // Try to add the number to the lottery book
            val number = lotteryArgs[1].toInt()
            if (lotteryBook.contains(number)) {
                response = "Lottery number $number already selected."
            } else {
                response = "Lottery number registered."
                lotteryBook.add(lotteryArgs[1].toInt())
            }
            val reply = createMail(clientPublicKey, response.toByteArray())
            postMail(reply, null)
            return
        }
    }
}