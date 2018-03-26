/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.nodes.memory.store;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.VariableBitWidthType;

public abstract class LLVMIVarBitStoreNode extends LLVMStoreNode {

    public LLVMIVarBitStoreNode(VariableBitWidthType type) {
        super(type, getSize(type));
    }

    private static int getSize(VariableBitWidthType type) {
        int nrFullBytes = type.getBitSize() / Byte.SIZE;
        if (type.getBitSize() % Byte.SIZE != 0) {
            nrFullBytes += 1;
        }
        return nrFullBytes;
    }

    @Specialization
    protected Object doOp(LLVMGlobal address, LLVMIVarBit value,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess,
                    @Cached("getLLVMMemory()") LLVMMemory memory) {
        memory.putIVarBit(globalAccess.executeWithTarget(address), value);
        return null;
    }

    @Specialization
    protected Object doOp(LLVMAddress address, LLVMIVarBit value,
                    @Cached("getLLVMMemory()") LLVMMemory memory) {
        memory.putIVarBit(address, value);
        return null;
    }

    @Specialization
    protected Object doForeign(LLVMTruffleObject address, LLVMIVarBit value,
                    @Cached("createForeignWrite()") LLVMForeignWriteNode foreignWrite) {
        byte[] bytes = value.getBytes();
        LLVMTruffleObject currentPtr = address;
        for (int i = bytes.length - 1; i >= 0; i--) {
            foreignWrite.execute(currentPtr, bytes[i]);
            currentPtr = currentPtr.increment(I8_SIZE_IN_BYTES);
        }
        return null;
    }

    @Override
    protected LLVMForeignWriteNode createForeignWrite() {
        return LLVMForeignWriteNodeGen.create(PrimitiveType.getIntegerType(I8_SIZE_IN_BITS), I8_SIZE_IN_BYTES);
    }
}
