/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.initialization;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.NativeContextExtension;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;

public final class LoadNativeNode extends RootNode {

    private final TruffleFile file;
    @CompilationFinal private ContextReference<LLVMContext> ctxRef;

    private LoadNativeNode(FrameDescriptor rootFrame, LLVMLanguage language, TruffleFile file) {
        super(language, rootFrame);
        this.file = file;
    }

    public static LoadNativeNode create(FrameDescriptor rootFrame, LLVMLanguage language, TruffleFile file) {
        return new LoadNativeNode(rootFrame, language, file);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        if (ctxRef == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            this.ctxRef = lookupContextReference(LLVMLanguage.class);
        }

        LoadModulesNode.LLVMLoadingPhase phase;
        if (frame.getArguments().length > 0 && (frame.getArguments()[0] instanceof LoadModulesNode.LLVMLoadingPhase)) {
            phase = (LoadModulesNode.LLVMLoadingPhase) frame.getArguments()[0];
        } else {
            throw new LLVMParserException("LoadNativeNode is called with unexpected arguments");
        }

        if (LoadModulesNode.LLVMLoadingPhase.INIT_SYMBOLS.isActive(phase)) {
            LLVMContext context = ctxRef.get();
            parseAndInitialiseNativeLib(context);
        }
        return null;
    }

    @TruffleBoundary
    private void parseAndInitialiseNativeLib(LLVMContext context) {
        NativeContextExtension nativeContextExtension = context.getContextExtensionOrNull(NativeContextExtension.class);
        if (nativeContextExtension != null) {
            CallTarget callTarget = nativeContextExtension.parseNativeLibrary(file, context);
            nativeContextExtension.addLibraryHandles(callTarget.call());
        }
    }
}
