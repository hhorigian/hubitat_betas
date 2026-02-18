/**
 *  Hubitat - TCP MolSmart Relay Drivers by VH - 
 *
 *  Copyright 2024 VH
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *        
 *        1.0 25/4/2024  - V.BETA 1
 *	      1.1 30/4/2024  - correção do indexof para verificar os digitos de Network ID. Lineas 449 e 494, precisa ver quantos digitos vai ter o network id. Agora ficou para começar
 * 	      depois do "5" digito para ver depspues del "-". 
 *        1.2 05/10/2024 - Adição do Check cada 5 minutos para keepalive. Adição de botão manual para KeepAlive. 
 *        1.3 05/12/2024 - Added BoardStatus Attribute (online/offline)
 *        1.4 05/22/2024 - Fix Scenes by adding "pauseExecution(250)" for On and Off in Childs 
 *        1.5 06/05/2024 - Added Help Guide Link
 *        1.6 13/06/2024 - Added Help Guide Link  v2
 *        1.7 26/06/2024 - Added "0" digit to switch name, to sort nicely the switch names. 
 *        1.8 27/06/2024 - Fixed double digit error on updates after v.1.7.  
 *        1.9 29/06/2024 - Added Board Status  Fix for Onlin/Offline to be used in Rule Machine Notifications + Improved Initialize/Update/Install Functions + Improved Logging + Added ManualKeepAlive Check Command.
 *        2.0 16/07/2024 - Fixed erro on line code 587 - MANDATORY UPDATE.
 *        2.1 30/07/2024 - Changed ouput status reading response method for TCP + Improved feedback response and status + Fixed false ghost feedback + Changed Master on/Master Off sequence with 250ms after each ch.  
 *        2.2 30/07/2024 - Fixed 16CH Count Relays. Fixed 32Ch.  Update for Long NetworkIds, used a new function to find index of in lines 959 and 1006. Added 32CH Master on/off.
 *        2.3 05/08/2024 - Added Line 278, with String thisId = device.id
 *        2.4 13/08/2024 - Added singleThreaded: true to metadata definition to fix Alexa Group issue
 *        2.5 17/02/2025 - Added 2CH Board. 
 *						 - Improved Reconnect function for disconnected board.  
 *						 - Updated and splitted code for Parsing, Added Status for Online/Offline. Changed Masteron/masteroff speeds and processing.  
 *						 - Changed Logging defaults. Added BoardStatus State variable and Notification OPtion. 
 *						 - UPDATE: Added Variable for Check Interval in seconds, 
 *						 - UPDATE: Added option for enable notifications. 
 *        2.6 17/02/2025 - Fixed Return Inputs for 2CH Board. 
 *        2.7 17/02/2025 - 30/06/2025 - Added Button for "Atualiza IP + Porta" se for alterada/trocada a placa da Mol. 
 *        2.8 11/8/2015 Changes:
 *                      - FIX: removed duplicated/overloaded txRaw() methods that caused compile error.
 *                      - Single txRaw(String msg, String reason) implementation (no default params).
 *                      - Features: PushableButton from inputs, terminator, HTTP control fallback, reconnect/heartbeat. Added HELD button for pushed inputs. 
 *        2.9 15/8/2015 Changes: • Per-input contact updates (open/closed) with timestamp (lastChange)
 * 							     • HTTP discovery used for channel count
 * 		  3.0 8/10/2025 - Added Power Monitor for MolSmart boards with support. 
 * 						- Added Module Restart (using MQTT cloud. Needs to be with internet). 
 * 						- Added Press and HOLD status feedback.  
 * 		  3.1 14/10/2025 - Added Module LAN Restart 
 * 		  3.2 21/10/2025 - Added support for New Modules firmware with 4 parts instead of 5 (ex.: rBits:iBits:CH:rMask:iMask, now: rBits:iBits:CH:rMask)
 * 		  3.3 13/1/2026 - Added Autodisable logdebug after 30minutes. Removed TCP Terminator from preferences. 
 * 		  3.4 26/1/2026 - Added auto-reboot and initialize when = socketStatus: send error: Broken pipe (Write failed)
 * 		  3.5 12/2/2026 - Added ckeepalive. 

* 
*/

import groovy.transform.Field

metadata {
  definition (
    name: "MolSmart - Relay 2/4/8/16/32CH (TCP)",
    namespace: "TRATO",
    author: "VH",
    importUrl: ""
  ) {
    capability "Initialize"
    capability "Refresh"
    capability "Actuator"
    capability "PushableButton"
    capability "HoldableButton"
    capability "Switch"
    command "detectAndSyncChannels"
    //command "sendReboot"
	command "sendRebootLAN"


    attribute "driverVersion", "string"    
    attribute "lastHeld", "number"
    attribute "lastPushed", "number"
    attribute "lastmessage", "string"
    attribute "lastRx", "number"
    attribute "online", "string"
    attribute "numberOfButtons", "number"   // equals input count
  }

  preferences {
//        input name: "pwrAlertEnable", type: "bool", title: "Habilitar alertas de baixa tensão (< limiar)?", defaultValue: true
//        input name: "pwrLowThreshold", type: "number", title: "Limiar de baixa tensão (V)", defaultValue: 11.5, range: "0..50"
//        input name: "pwrHysteresis", type: "number", title: "Histerese para limpar alerta (V)", defaultValue: 0.3, range: "0..5"
        input name: "enablePwrMonitor", type: "bool", title: "Habilitar monitoramento de voltagem (pwr.cgi)?", defaultValue: false
        input name: "pwrPollSec", type: "number", title: "Intervalo de leitura (segundos)", defaultValue: 60, range: "5..3600"
        input name: "autoPruneChildren", type: "bool", title: "Remover filhos excedentes ao sincronizar?", defaultValue: true
        input name: "autoCreateChildren", type: "bool", title: "Criar filhos automaticamente após descobrir canais?", defaultValue: true
        input name: "molsmartSN", type: "text", title: "Numero de Serie da Mol",  required: false
      
    // Network
    input name: "ipAddress",   type: "text",   title: "IP do módulo", required: true
    input name: "ipPort",      type: "number", title: "Porta TCP", defaultValue: 502, range: "1..65535"

    // Optional HTTP fallback/auth (kept from Relay driver)
    input name: "httpUser",    type: "text",     title: "Usuário HTTP (opcional)"
    input name: "httpPass",    type: "password", title: "Senha HTTP (opcional)"
    input name: "sessionCookie", type: "text",   title: "Cookie (opcional)"

    // Heartbeat / reconnection
    input name: "hbInterval",  type: "number", title: "Keep-alive (segundos)", defaultValue: 15, range: "5..120", required: true
    input name: "idleTimeout", type: "number", title: "Timeout de inatividade (s)", defaultValue: 60, range: "10..600", required: true
    input name: "reconnectMin",type: "number", title: "Backoff mínimo (s)", defaultValue: 5,  range: "1..120", required: true
    input name: "reconnectMax",type: "number", title: "Backoff máximo (s)", defaultValue: 60, range: "5..600", required: true

    // TCP terminator
    //input name: "tcpTerminator", type: "enum", title: "Terminador TCP", defaultValue: "NONE",
    //  options: ["NONE","LF","CR","CRLF"],
    //  description: "Se 11/21/1X/2X não forem aceitos, tente CR ou CRLF."

    // Inputs → contacts/buttons
    input name: "inputsActiveLow", type: "bool", title: "Entradas ativas em nível baixo? (0 = pressionado)", defaultValue: true
    input name: "inputsNormalOpen", type: "bool", title: "Contato lógico normal = OPEN?", defaultValue: false
    input name: "buttonDebounceMs", type: "number", title: "Debounce (ms)", defaultValue: 120, range: "0..2000"

    // Logging
    input name: "logEnable", type: "bool", title: "Logs de debug", defaultValue: true
  }        

            attribute "Entrada12V-1", "text"
            attribute "Entrada12V-2", "text"
            attribute "pwrVoltages", "text"
		    attribute "pwrLow", "STRING"    
//    		attribute "pwrStatus", "number"
//          attribute "pwrCount", "number"
//          attribute "pwrV1", "number"
//          attribute "pwrV2", "number"
//		    attribute "pwr1Low", "STRING"
//		    attribute "pwr2Low", "STRING"
		    attribute "pwrAlertText", "STRING"
		    attribute "lastPwrAlertAt", "STRING"

}


@Field static String rxBuf = ''
@Field static java.util.Random _rng = new java.util.Random()
@Field static Long lastHbSentAt = 0L
@Field static final String BROKER_HOST = "bms.molsmart.com.br"
@Field static final Integer BROKER_PORT = 1883
@Field static final String USERNAME = "molsmartboardbms"
@Field static final String PASSWORD = ''
@Field static final String PAYLOAD  = '{"command":"reboot"}'
@Field static final Integer DISCONNECT_DELAY_SEC = 1
@Field static final Integer CONNECT_TIMEOUT_SEC  = 10
@Field static final String TCP_TERMINATOR = "NONE"   // ou "CR" / "CRLF" se for o caso
@Field static final String DRIVER_VERSION = "3.3"


/* ===== Logger helpers ===== */
private void logDbg(msg){ if (settings?.logEnable) log.debug("${device.displayName ?: device.name}: ${msg}") }
private void logInf(msg){ log.info ("${device.displayName ?: device.name}: ${msg}") }
private void logWar(msg){ log.warn ("${device.displayName ?: device.name}: ${msg}") }
private void logErr(msg){ log.error("${device.displayName ?: device.name}: ${msg}") }

/* ===== Small utils ===== */
private boolean socketUp()     { (state.socketOnline ?: false) as boolean }
private void   markRxNow()     { state.lastRx = now(); sendEvent(name:"lastRx", value: state.lastRx) }
private Long   msSinceRx()     { def t = (state.lastRx ?: 0L) as Long; return (t>0L) ? (now()-t) : Long.MAX_VALUE }
private int    clampInt(v,l,h){ Math.max(l as int, Math.min(h as int, (v ?: 0) as int)) }
private String netIdPrefix()   { state.netids ?: (state.netids = device.deviceNetworkId ?: device.id.toString()) }
private String inPrefix()      { state.inNetIds ?: (state.inNetIds = netIdPrefix()+"IN") } // inputs use INxx

private String resolveIP(){ String ip = settings?.ipAddress ?: state?.ipAddress ?: state?.ipaddress; return ip?.trim() }
private Integer resolvePort(){ (settings?.ipPort as Integer) ?: 502 }

/* ================= Lifecycle ================= */

def installed(){ 
    sendEvent(name: "driverVersion", value: DRIVER_VERSION)    
	device.updateSetting("logEnable", [value:"false", type:"bool"])    
    initialize() 
}

def updated(){ 
    // limpa schedules antigos de versões anteriores
    try { unschedule("hbAckCheck") } catch(e) {}
    unschedule(); disconnectSocket(); 
    if (settings?.logEnable) runIn(1800, "logsOff")   // agenda auto-off se estiver ligado    
    sendEvent(name: "driverVersion", value: DRIVER_VERSION)
    initialize() 
             }

def uninstalled(){ unschedule(); disconnectSocket() }

/* initialize: discover channel count, create children (switches + contacts), connect */


def initialize(){
    logInf("Initialize")
    // limpa schedules antigos de versões anteriores
    try { unschedule("hbAckCheck") } catch(e) {}
    String ip = resolveIP()
    if (!ip){
        logWar("IP do módulo ainda não configurado. Sem criação de filhos até configurar o IP.")
        sendEvent(name:"online", value:"false")
        state.socketOnline = false
        return
    }
    Integer ch = null
    try { ch = discoverChannelCount() as Integer } catch (e) { logWar("discoverChannelCount falhou: ${e}") }
    if (!ch || ch <= 0){
        logWar("Quantidade de canais desconhecida. Não criarei filhos até detectar com sucesso. Use o comando 'Detect & Sync Channels'.")
        connectSocket()
        runIn(2, "queryBoardStatus", [overwrite:true])
        schedulePwrPolling()
        return
    }
    state.inputcount = ch
    sendEvent(name:"numberOfButtons", value: ch)
    state.lastButtons = ch

    if (settings?.autoCreateChildren != false){
        try { syncChildren(ch) } catch (e) { logWar("syncChildren falhou: ${e}") }
    } else {
        logInf("Criação automática de filhos está desativada (autoCreateChildren=false).")
    }

    connectSocket()
    runIn(2, "queryBoardStatus", [overwrite:true])
    schedulePwrPolling()
    //sendEvent(name: "driverVersion", value: DRIVER_VERSION)
	if (settings?.logEnable) runIn(1800, "logsOff", [overwrite:true])
    
    
}



/* ================= Power Voltage (pwr.cgi) Monitoring ================= */
private void schedulePwrPolling(){
    try{ unschedule('pwrPollTick') } catch(e){}
    Integer sec = (settings?.pwrPollSec ?: 60) as Integer
    if (!(settings?.enablePwrMonitor)){
        logDbg("Monitoramento de voltagem desabilitado.")
        return
    }
    if (sec < 5) sec = 5
    logInf("Agendando leitura de voltagem (pwr.cgi) a cada ${sec}s")
    runIn(sec, 'pwrPollTick', [overwrite:true])
}

def pwrPollTick(){
    if (!(settings?.enablePwrMonitor)) return
    doAsyncPwrQuery()
    Integer sec = (settings?.pwrPollSec ?: 60) as Integer
    if (sec < 5) sec = 5
    runIn(sec, 'pwrPollTick', [overwrite:true])
}

private void doAsyncPwrQuery(){
    String ip = resolveIP()
    if (!ip){ logWar("pwr.cgi: IP não configurado."); return }
    Map params = [ uri: "http://${ip}/api/v2/pwr.cgi", headers: httpHeaders(), timeout: 5 ]
    try{
        asynchttpGet('pwrHttpCallback', params)
    } catch(e){
        logWar("Falha ao iniciar asynchttpGet para pwr.cgi: ${e}")
    }
}

def pwrHttpCallback(resp, data){
  try{
    if (!resp){ logWar("pwr.cgi: sem resposta"); return }
    if (resp?.hasError()){ logWar("pwr.cgi: HTTP erro ${resp?.getStatus()}"); return }

    String raw = resp?.getData() as String
    if (!raw){ logWar("pwr.cgi: corpo vazio"); return }

    def js = null
    try{ js = parseJson(raw) } catch(ex){ logWar("pwr.cgi: JSON inválido: ${ex}"); return }

    Integer status = (js?.status instanceof Number) ? (js.status as Integer)
                   : (js?.status?.toString()?.isInteger() ? js.status.toString().toInteger() : null)
    Integer cnt    = (js?.cnt    instanceof Number) ? (js.cnt as Integer)
                   : (js?.cnt?.toString()?.isInteger()    ? js.cnt.toString().toInteger()    : null)

    List vlist = (js?.v instanceof List) ? (List)js.v : []
    //if (status != null) sendEvent(name:"pwrStatus", value: status)
    //if (cnt != null)    sendEvent(name:"pwrCount",  value: cnt)

    BigDecimal v1 = null
    BigDecimal v2 = null

    if (vlist.size() >= 1){
      String s1 = (vlist[0]?.toString() ?: "").trim()
      if (s1) sendEvent(name:"Entrada12V-1", value: s1)
      String n1 = s1.replaceAll(/[^0-9\.\-]/,'')
      if (n1){ try{ v1 = new BigDecimal(n1) }catch(e){} }
      //if (v1 != null) sendEvent(name:"pwrV1", value: v1, unit:"V")
    }
    if (vlist.size() >= 2){
      String s2 = (vlist[1]?.toString() ?: "").trim()
      if (s2) sendEvent(name:"Entrada12V-2", value: s2)
      String n2 = s2.replaceAll(/[^0-9\.\-]/,'')
      if (n2){ try{ v2 = new BigDecimal(n2) }catch(e){} }
      //if (v2 != null) sendEvent(name:"pwrV2", value: v2, unit:"V")
    }

    //processPwrAlerts(v1, v2)   // <- aqui disparo/limpo alertas c/ histerese
    String joined = vlist.collect{ it?.toString() ?: "" }.join(", ")
    //if (joined) sendEvent(name:"pwrVoltages", value: joined)
    logDbg("pwr.cgi -> status=${status}, cnt=${cnt}, v=${joined}")
  } catch(e){
    logWar("pwrHttpCallback erro: ${e}")
  }
}



@Field static Boolean _reconnectLoopActive = false

private void startReconnectLoop(String reason){
  if (_reconnectLoopActive == true) return
  _reconnectLoopActive = true
  logWar("Iniciando loop de reconexão (${reason})")
  runIn(1, "reconnectLoop", [overwrite:true])
}

def reconnectLoop(){
  if (socketUp()){
    _reconnectLoopActive = false
    return
  }
  // Tenta conectar periodicamente até sucesso
  try{
    connectSocket()
  } catch(e){
    // connectSocket já loga; só garante que o loop continue
  }
  if (!socketUp()){
    int minS = clampInt(settings?.reconnectMin, 1, 120)
    runIn(minS, "reconnectLoop", [overwrite:true])
  } else {
    _reconnectLoopActive = false
  }
}

/* ================= Socket & Connection ================= */

private void connectSocket(){
  String ip = resolveIP(); Integer port = resolvePort()
  if (!ip){
    logErr("IP não configurado nas Preferences.")
    sendEvent(name:"online", value:"false"); state.socketOnline = false
    unschedule("heartbeat"); unschedule("connectionCheck")
    return
  }
  try{
    interfaces.rawSocket.connect(ip, port as int)
    state.socketOnline = true
    sendEvent(name:"online", value:"true")
    state.conIp = ip; state.conPort = port
    logInf("Socket conectado a ${ip}:${port}")
    // Ao reconectar, cancela loop antigo
    try { unschedule("reconnectLoop") } catch(ignored) {}
    _reconnectLoopActive = false
    state.connectedAt = now()
    state.reconnectAttempt = 0
    markRxNow()
    scheduleHeartbeat(); scheduleWatchdog(); runIn(1, "heartbeat", [overwrite:true])
  } catch(e){
    logErr("Falha ao conectar em ${ip}:${port}: ${e}")
    state.socketOnline = false
    sendEvent(name:"online", value:"false")
    // mantém tentativas de reconexão vivas
    startReconnectLoop("connect error")
  }
}

private void disconnectSocket(){ try { interfaces.rawSocket.close() } catch(ignored){}; state.socketOnline=false; sendEvent(name:"online", value:"false") }

private void scheduleHeartbeat(){ int s = clampInt(settings?.hbInterval,5,120); runIn(s, "heartbeat", [overwrite:true]) }

def heartbeat(){
  if (!socketUp()){
    logWar("HB: socket down -> loop reconexão")
    startReconnectLoop("HB socket down")
    return
  }
  lastHbSentAt = now()
  txRaw("00", "HB")
  scheduleHeartbeat()
}



private void scheduleWatchdog(){ runIn(5, "connectionCheck", [overwrite:true]) }

def connectionCheck(){
  // Watchdog "seco" e resiliente: continua rodando mesmo OFFLINE.
  long sinceConn = now() - ((state.connectedAt ?: 0L) as long)

  if (!socketUp()){
    // Se o socket caiu, mantém o watchdog vivo e inicia loop de reconexão.
    sendEvent(name:"online", value:"false")
    state.socketOnline = false
    try { disconnectSocket() } catch(e) {}
    startReconnectLoop("socket down (watchdog)")
    scheduleWatchdog()
    return
  }

  // Dá um tempo após conectar (módulo pode demorar para responder a primeira leitura)
  if (sinceConn > 0 && sinceConn < 15000L){
    scheduleWatchdog()
    return
  }

  int hb = clampInt(settings?.hbInterval,5,120)
  int userIdle = clampInt(settings?.idleTimeout,10,600)
  int effIdle = Math.max(userIdle, hb*3)
  long idle = msSinceRx()

  if (idle >= effIdle*1000L){
    logErr("Sem RX há ${String.format('%.1f', idle/1000.0)}s (limite=${effIdle}s). Módulo OFFLINE -> reconectar")
    sendEvent(name:"online", value:"false")
    state.socketOnline = false
    try { disconnectSocket() } catch(e) {}
    startReconnectLoop("idle timeout")
    scheduleWatchdog()
    return
  }

  scheduleWatchdog()
}

private void scheduleReconnect(String reason){
  if (state.reconnecting == true) return
  state.reconnecting = true
  state.reconnectingSince = now()
  try { disconnectSocket() } catch(e) {}
  int attempt = ((state.reconnectAttempt ?: 0) as int) + 1
  state.reconnectAttempt = attempt
  int minS = clampInt(settings?.reconnectMin,1,120)
  int maxS = clampInt(settings?.reconnectMax,5,600)
  int delay = Math.min(maxS, (int)Math.pow(2D, Math.min(6,attempt-1)) * minS) + _rng.nextInt(1)
  logWar("Reconectar (#${attempt}) em ~${delay}s (${reason})")
  runIn(delay, "doReconnect", [overwrite:true])
}

def doReconnect(){ state.reconnecting = false; state.reconnectingSince = 0L; connectSocket() }


def socketStatus(String message){
    logWar("socketStatus: ${message}")
    if ((message ?: "").toLowerCase().contains("broken pipe")){
        noteBrokenPipeAndMaybeReboot("socketStatus")
    }
    if (!(message?.toLowerCase()?.contains("normal") ?: false)) scheduleReconnect("socketStatus")
}


/* ================= Parser ================= */

def parse(String payload){
  if (!payload) return
  markRxNow()
  // HEX -> texto (se vier em hexa contínuo)
  if (payload ==~ /(?i)^[0-9A-F]+$/ && (payload.length() % 2 == 0)){
    try { payload = new String(hubitat.helper.HexUtils.hexStringToByteArray(payload)) } catch(e){}
  }

  payload = payload.replaceAll(/[\r\n]+/, ' ')
  rxBuf += payload
  logDbg("RX chunk: ${payload.trim()} (buffer=${rxBuf.length()})")

  while(true){
    rxBuf = rxBuf.replaceFirst(/^\s+/, '')
    if (!rxBuf) break

    int sp = rxBuf.indexOf(' ')
    String token = (sp >= 0) ? rxBuf.substring(0, sp) : rxBuf

    // AGORA aceitamos 3 ou 4 dois-pontos (4 ou 5 partes)
    int colons = token.findAll(':').size()
    if (colons < 3 && sp < 0) break
    if (colons < 3){ rxBuf = (sp >= 0) ? rxBuf.substring(sp+1) : ''; continue }

    String[] parts = token.split(':')
    if (parts.length != 4 && parts.length != 5){
      if (sp < 0) break
      logDbg("Descartando token inválido (partes=${parts.length}): ${token}")
      rxBuf = (sp >= 0) ? rxBuf.substring(sp+1) : ''
      continue
    }

    String rBits = parts[0]     // estados dos relés
    String iBits = parts[1]     // estados das entradas (pode conter 'H' no hold)
    Integer ch = null
    try { ch = parts[2].toInteger() } catch(ignored){}

    // rMask sempre presente; iMask pode faltar no FW novo
    String rMask = (parts.length >= 4) ? parts[3] : null
    String iMask = (parts.length == 5) ? parts[4] : null

    // Se iMask não vier no novo FW, usamos zeros (mesmo tamanho de ch)
    if (iMask == null && ch != null && ch > 0) {
      iMask = "0" * ch
    }

    boolean sizesOk = (ch != null && ch > 0
      && rBits?.size()==ch && iBits?.size()==ch && rMask?.size()==ch && iMask?.size()==ch)

    boolean patternsOk = (rBits   ==~ /^[01]+$/
      && iBits ==~ /^[01H]+$/
      && rMask ==~ /^[01]+$/
      && iMask ==~ /^[01H0]+$/) // quando gerado, é "0...0"; ainda aceitamos 'H' por compatibilidade

    if (!(sizesOk && patternsOk)){
      if (sp < 0) break
      logDbg("Frame inválido descartado: ${token}")
      rxBuf = (sp >= 0) ? rxBuf.substring(sp+1) : ''
      continue
    }

    // Consumir o token processado
    rxBuf = (sp >= 0) ? rxBuf.substring(sp+1) : ''

    // Atualizar filhos de relé (switch)
    for (int i=0; i<ch; i++){
      String swDni = childRelayDni(i+1)
      def cd = getChildDevice(swDni)
      if (cd) cd.parse([[name:"switch", value: (rBits.charAt(i)=='1') ? "on" : "off"]])
    }

    // Entradas -> contatos + push/held (iBits carrega 'H'; se FW antigo, também pode vir em iMask)
    handleInputEventsAndContacts(iBits, iMask, ch)

    sendEvent(name:"lastmessage", value: token)
    if ((state?.lastButtons as Integer) != ch){ sendEvent(name:"numberOfButtons", value: ch); state.lastButtons = ch }
  }
}


/* ================= Inputs: Buttons + Contacts ================= */


/* ================= Inputs: Buttons + Contacts ================= */
private void handleInputEventsAndContacts(String iBits, String iMask, int chan){
  if (!iBits || chan <= 0) return

  // snapshot anterior; default = liberado
  String prev = state.prevInputBits ?: ("1" * chan)
  if (prev.length() != iBits.length()) prev = ("1" * iBits.length())

  int debounce = clampInt(settings?.buttonDebounceMs, 0, 2000)
  boolean activeLow   = (settings?.inputsActiveLow   != false)  // default true
  boolean normalOpen  = (settings?.inputsNormalOpen  != false)  // default true
  long nowMs = now()
  String ts = new Date().format("yyyy-MM-dd HH:mm:ss")

  // estados por botão
  if (state.btnLastMs       == null) state.btnLastMs       = [:]  // último PUSH emitido (ms)
  if (state.btnLastHeldMs   == null) state.btnLastHeldMs   = [:]  // último HELD emitido (ms)
  if (state.btnPendingPush  == null) state.btnPendingPush  = [:]  // true após PRESS até RELEASE
  if (state.btnHeldCycle    == null) state.btnHeldCycle    = [:]  // houve HELD neste ciclo (entre press e release)

  for (int i=0; i<chan; i++){
    char before = (i < prev.length()) ? prev.charAt(i) : '1'
    char after  = iBits.charAt(i)
    char m      = (iMask && i < iMask.length()) ? iMask.charAt(i) : '0'
    int idx = i+1

    // interpretado considerando activeLow e 'H'
    boolean wasPressed = activeLow ? (before=='0' || before=='H') : (before=='1' || before=='H')
    boolean nowPressed = activeLow ? (after =='0' || after =='H') : (after =='1' || after =='H')

    boolean pressEdge   = (!wasPressed &&  nowPressed)
    boolean releaseEdge = ( wasPressed && !nowPressed)
    boolean holdSeenFrm = (after=='H') || (m=='H')

    // Atualiza CONTACT do filho
    String contactState = nowPressed ? (normalOpen ? "closed" : "open")
                                     : (normalOpen ? "open"   : "closed")
    String inDni = childInputDni(idx)
    def inChild = getChildDevice(inDni)
    if (inChild){
      if (inChild.currentValue("contact") != contactState){
        inChild.sendEvent(name:"contact", value: contactState)
      }
    }

    // Novo ciclo: press -> marca push pendente
    if (pressEdge){
      state.btnPendingPush["${idx}"] = true
      state.btnHeldCycle  ["${idx}"] = false
    }

    // HOLD visto em qualquer frame do ciclo => suprime push
    if (holdSeenFrm){
      long lastHeld = (state.btnLastHeldMs["${idx}"] ?: 0L) as Long
      if (debounce <= 0 || (nowMs - lastHeld) >= debounce){
        sendEvent(name:"held", value: idx, isStateChange:true, type:"physical", descriptionText: "Input ${idx} held")
        sendEvent(name:"lastHeld", value: idx)
        state.btnLastHeldMs["${idx}"] = nowMs
      }
      state.btnHeldCycle["${idx}"] = true
      state.btnPendingPush["${idx}"] = false   // cancela push deste ciclo
    }

    // RELEASE => se ainda houver push pendente e não houve hold no ciclo, emite PUSH
    if (releaseEdge){
      boolean pending = (state.btnPendingPush["${idx}"] ?: false) as boolean
      boolean heldcyc = (state.btnHeldCycle  ["${idx}"] ?: false) as boolean
      if (pending && !heldcyc){
        long lastMs = (state.btnLastMs["${idx}"] ?: 0L) as Long
        if (debounce <= 0 || (nowMs - lastMs) >= debounce){
          sendEvent(name:"pushed", value: idx, isStateChange:true, type:"physical", descriptionText: "Input ${idx} pushed")
          sendEvent(name:"lastPushed", value: idx)
          state.btnLastMs["${idx}"] = nowMs
        } else {
          logDbg("Input ${idx} push ignorado pelo debounce (${nowMs-lastMs}ms < ${debounce}ms)")
        }
      }
      // fim do ciclo
      state.btnPendingPush["${idx}"] = false
      state.btnHeldCycle  ["${idx}"] = false
    }

    // Atualiza atributos do filho com status de hold/combined
    if (inChild){
      String holdStatus = ((state.btnHeldCycle["${idx}"] ?: false) || holdSeenFrm) ? "active" : "inactive"
      if ((inChild.currentValue("holdStatus") ?: "inactive") != holdStatus){
        inChild.sendEvent(name:"holdStatus", value: holdStatus)
      }
      String combined = (contactState == "closed") ? (holdStatus=="active"?"held-closed":"closed")
                                                   : (holdStatus=="active"?"held-open":"open")
      inChild.sendEvent(name:"combinedStatus", value: combined)
      String tag
      if (holdSeenFrm)      tag = "(Hold)"
      else if (pressEdge)   tag = "(Press)"
      else if (releaseEdge) tag = "(Release)"
      else                  tag = ""
      if (tag) inChild.sendEvent(name:"lastChange", value: "${ts} ${tag}")
    }
  }

  state.prevInputBits = iBits // guarda inclusive 'H'
}


/* ================= Commands ================= */

def refresh(){ logInf("Refresh() -> 00"); txRaw("00", "manual refresh") }

// Master ON/OFF sequential to avoid bursts

def masteron(){
  logInf("Master ON (100ms/relay)")
  int ch = (state?.inputcount ?: getChildDevices()?.findAll{ it.typeName.contains('Switch') }?.size() ?: 8) as int
  for (int i=1; i<=ch; i++){ doControlTX("1${i}", i, true); pauseExecution(100) }
}

def masteroff(){
  logInf("Master OFF (100ms/relay)")
  int ch = (state?.inputcount ?: getChildDevices()?.findAll{ it.typeName.contains('Switch') }?.size() ?: 8) as int
  for (int i=1; i<=ch; i++){ doControlTX("2${i}", i, false); pauseExecution(100) }
}

// Component callbacks for switch children

def componentOn(cd){ Integer ch = relayIndexFromDni(cd?.deviceNetworkId); if (ch!=null){ log.info("on from ${cd?.displayName}");  doControlTX("1${ch}", ch, true) } }

def componentOff(cd){ Integer ch = relayIndexFromDni(cd?.deviceNetworkId); if (ch!=null){ log.info("off from ${cd?.displayName}"); doControlTX("2${ch}", ch, false) } }

private void doControlTX(String cmd, Integer ch=null, Boolean on=null){
  // TX over TCP socket; simple refresh after
  boolean ok = txRaw(cmd, "control")
  runIn(1, "refresh", [overwrite:true])
}

/* Single txRaw implementation */
private boolean txRaw(String msg, String reason){
  String tx = withTerminator(msg)
  try{
    interfaces.rawSocket.sendMessage(tx)
    //logDbg("TX${reason?"(${reason})":''}: '"+escapePrint(msg)+"' + term='${settings?.tcpTerminator ?: "NONE"}' (len=${tx.length()})")
	  logDbg("TX${reason?"(${reason})":''}: '"+escapePrint(msg)+"' + term='${TCP_TERMINATOR}' (len=${tx.length()})")
      
    if (msg == "00") lastHbSentAt = now()
    return true
} catch(e){
    logWar("Falha ao enviar '${msg}': ${e}")
    if (("${e}".toLowerCase().contains("broken pipe"))){
        noteBrokenPipeAndMaybeReboot("txRaw:${reason}")
    }
    scheduleReconnect("send fail")
    return false
}
}






/* ================= Children creation ================= */

private String childRelayDni(int idx){ return "${netIdPrefix()}${idx.toString().padLeft(2,'0')}" }
private String childInputDni(int idx){ return "${inPrefix()}${idx.toString().padLeft(2,'0')}" }

private Integer relayIndexFromDni(String dni){
  if (!dni) return null
  String prefix = netIdPrefix()
  if (!dni.startsWith(prefix)) return null
  try { return dni.substring(prefix.length()).toInteger() } catch(e){ return null }
}


private void createRelayChildren(){
    int ch = (state?.inputcount ?: 0) as int
    if (ch <= 0){
        logWar("Canais ainda não detectados; não criarei relays.")
        return
    }
    (1..ch).each { n ->
        String dni = childRelayDni(n)
        if (!getChildDevice(dni)){
            try{
                addChildDevice("hubitat", "Generic Component Switch", dni,
                    [name: "Mol Relay ${n.toString().padLeft(2,'0')}", label: "Mol Relay ${n.toString().padLeft(2,'0')}", isComponent: true])
                logInf("Child SWITCH criado: ${dni}")
            } catch(e){ logErr("Falha ao criar switch ${dni}: ${e}") }
        }
    }
}



private void createInputChildren(){
    int ch = (state?.inputcount ?: 0) as int
    if (ch <= 0){
        logWar("Canais ainda não detectados; não criarei inputs.")
        return
    }
    (1..ch).each { n ->
        String dni = childInputDni(n)
        if (!getChildDevice(dni)){
            try{
                def c = addChildDevice("hubitat", "Generic Component Contact Sensor", dni,
                    [name: "Mol Input ${n.toString().padLeft(2,'0')}", label: "Mol Input ${n.toString().padLeft(2,'0')}", isComponent: true])
                logInf("Child CONTACT criado: ${dni}")
                c.sendEvent(name:"contact", value:"unknown")
                c.sendEvent(name:"holdStatus", value:"inactive")
                c.sendEvent(name:"combinedStatus", value:"unknown")
            } catch(e){ logErr("Falha ao criar contact ${dni}: ${e}") }
        }
    }
}


/* ================= HTTP helpers (for discovery/status) ================= */

private Map httpHeaders(){
  Map h = [:]
  if (settings?.sessionCookie) h.Cookie = settings.sessionCookie
  if (settings?.httpUser)     h.Authorization = "Basic " + "${settings.httpUser}:${settings.httpPass ?: ''}".bytes.encodeBase64()
  return h
}


private Integer discoverChannelCount(){
    String ip = resolveIP()
    if (!ip){
        logWar("discoverChannelCount: IP não configurado.")
        return null
    }
    
    try{
        Integer count = null
        httpGet([ uri: "http://${ip}/relay_cgi_load.cgi", headers: httpHeaders(), timeout: 5 ]) { resp ->
            String s = resp.data?.toString() ?: ''
            def parts = s.split('&')
            if (parts.size() > 2 && parts[2].isInteger()) count = parts[2].toInteger()
        }
        if (count && count > 0){
            logInf("Canais detectados (via HTTP): ${count}")
            
                    // ==== Novo: também ler SN do módulo ====
         // ==== Novo: também ler SN do módulo (tolerante a JSON e {k=v}) ====
        try {
            httpGet([ uri: "http://${ip}/get/sn.cgi", headers: httpHeaders(), timeout: 5 ]) { resp2 ->
                String raw2 = resp2.data?.toString() ?: ''
                String snVal = extractSnFlexible(raw2)
                if (!snVal && resp2?.data instanceof Map) {
                    // Em alguns firmwares o httpGet já converte em Map (GPath)
                    def js2 = (Map)resp2.data
                    if (js2?.sn != null) snVal = js2.sn.toString().trim()
                }
                if (snVal) {
                    device.updateSetting("molsmartSN", [value: snVal, type: "text"])
                    state.NumeroSerie = snVal
                    logInf("Número de série detectado: ${snVal}")
                } else {
                    logDbg("SN não encontrado no conteúdo de sn.cgi: ${raw2}")
                }
            }
        } catch (err2) {
            logWar("Falha ao obter número de série (sn.cgi): ${err2}")
        }

  
            return count
        } else {
            logWar("Não foi possível interpretar a quantidade de canais via HTTP.")
            return null
        }
    } catch(e){
        logWar("discoverChannelCount falhou: ${e}")
        return null
    }
}



def queryBoardStatus(){
    String ip = resolveIP()
    if (!ip) { logWar("queryBoardStatus: IP não configurado."); return }
    try{
        httpGet([ uri: "http://${ip}/relay_cgi_load.cgi", headers: httpHeaders(), timeout: 5 ]) { resp ->
            String s = resp.data?.toString() ?: ''
            logDbg("HTTP status raw: ${s}")
            def parts = s.split('&')
            if (parts.size() > 2 && parts[2].isInteger()){
                int ch = parts[2].toInteger()
                if (ch > 0){
                    if ((state?.inputcount ?: 0) != ch){
                        logInf("Board reportou ${ch} canais (antes: ${state?.inputcount ?: 0}). Sincronizando filhos...")
                        state.inputcount = ch
                        sendEvent(name:"numberOfButtons", value: ch)
                        state.lastButtons = ch
                        if (settings?.autoCreateChildren != false) syncChildren(ch)
                    } else {
                        sendEvent(name:"numberOfButtons", value: ch)
                        state.lastButtons = ch
                    }
                }
            }
        }
    } catch(e){ logWar("queryBoardStatus falhou: ${e}") }
}


/* ===== Helper: extrai SN de JSON OU mapa {k=v} ===== */
private static String extractSnFlexible(String s){
    if (!s) return null
    String t = s.trim()

    // 1) Tentar JSON padrão
    try {
        def js = new groovy.json.JsonSlurper().parseText(t)
        def v = js?.sn
        if (v != null) return v.toString().trim()
    } catch(ex) {
        // segue para regex
    }

    // 2) Tentar regexs tolerantes (suporta : ou =, com/sem aspas)
    def m = (t =~ /(?i)\b"sn"\s*:\s*"?([\w\-\.\:]+)"?/)
    if (m.find()) return m.group(1)

    m = (t =~ /(?i)\bsn\s*[=:]\s*"?([\w\-\.\:]+)"?/)
    if (m.find()) return m.group(1)

    // 3) Se vier como Map.toString(): {sn=51566, status=0}
    if (t.startsWith("{") && t.endsWith("}") && t.contains("=")) {
        String body = t.substring(1, t.length()-1)
        body.split(/\s*,\s*/).each { pair ->
            def kv = pair.split(/\s*[=:]\s*/, 2)
            if (kv.size() == 2 && kv[0].trim().equalsIgnoreCase("sn")) {
                return kv[1].trim().replaceAll(/^"(.*)"$/, "\$1")
            }
        }
    }
    return null
}


/* ================= String helpers ================= */
private String withTerminator(String s){
  //String t = (settings?.tcpTerminator ?: "NONE").toString().toUpperCase()
  String t = (TCP_TERMINATOR ?: "NONE").toString().toUpperCase()
    
  switch(t){
    case "CR":   return s + "\r"
    case "CRLF": return s + "\r\n"
    default:     return s
  }
}

private static String escapePrint(String s){
  return (s ?: "").replace("\r","\\r").replace("\n","\\n")
}


/* Sync children to exactly 'ch' relays + 'ch' inputs */
private void syncChildren(int ch){
    if (ch <= 0){
        logWar("syncChildren: valor de canais inválido (${ch}).")
        return
    }
    state.inputcount = ch

    // Create missing
    createRelayChildren()
    createInputChildren()

    // Optionally prune extras
    if (settings?.autoPruneChildren != false){
        String rPrefix = netIdPrefix()
        String iPrefix = inPrefix()
        getChildDevices()?.each { dev ->
            String dni = dev.deviceNetworkId ?: ""
            if (dni.startsWith(rPrefix)){
                String idxs = dni.substring(rPrefix.length())
                Integer idx = (idxs.isInteger() ? idxs.toInteger() : null)
                if (idx == null || idx < 1 || idx > ch){
                    logInf("Removendo relay filho excedente: ${dni}")
                    deleteChildDevice(dni)
                }
            } else if (dni.startsWith(iPrefix)){
                String idxs = dni.substring(iPrefix.length())
                Integer idx = (idxs.isInteger() ? idxs.toInteger() : null)
                if (idx == null || idx < 1 || idx > ch){
                    logInf("Removendo input filho excedente: ${dni}")
                    deleteChildDevice(dni)
                }
            }
        }
    } else {
        logInf("autoPruneChildren=false: filhos excedentes foram mantidos.")
    }
}



def detectAndSyncChannels(){
    Integer ch = null
    try { ch = discoverChannelCount() as Integer } catch (e) { logWar("discoverChannelCount falhou: ${e}") }
    if (!ch || ch <= 0){
        logWar("Não foi possível detectar a quantidade de canais neste momento.")
        return
    }
    state.inputcount = ch
    sendEvent(name:"numberOfButtons", value: ch)
    state.lastButtons = ch
    syncChildren(ch)
    logInf("Sincronização concluída com ${ch} canais.")
}



/* Parent Switch capability - controls ALL relays */
def on(){
    int ch = (state?.inputcount ?: getChildDevices()?.findAll{ it.typeName?.contains('Switch') }?.size() ?: 0) as int
    if (ch<=0){
        logWar("ON (Switch) cancelado: canais desconhecidos. Use o comando 'Detect & Sync Channels'.")
        return
    }
    logInf("Switch ON: ligando todos os ${ch} relays.")
    for (int i=1; i<=ch; i++){
        doControlTX("1${i}", i, true)
        pauseExecution(100)
    }
    sendEvent(name:"switch", value:"on")
}

def off(){
    int ch = (state?.inputcount ?: getChildDevices()?.findAll{ it.typeName?.contains('Switch') }?.size() ?: 0) as int
    if (ch<=0){
        logWar("OFF (Switch) cancelado: canais desconhecidos. Use o comando 'Detect & Sync Channels'.")
        return
    }
    logInf("Switch OFF: desligando todos os ${ch} relays.")
    for (int i=1; i<=ch; i++){
        doControlTX("2${i}", i, false)
        pauseExecution(100)
    }
    sendEvent(name:"switch", value:"off")
}

/* ===== LAN Reboot via HTTP (no MQTT required) ===== */
def sendRebootLAN(){
  String ip = resolveIP()
  if (!ip){
    logWar("sendRebootLAN: IP não configurado.")
    return
  }
  Map params = [ uri: "http://${ip}/reboot.cgi", headers: httpHeaders(), timeout: 5 ]
  try{
    httpGet(params) { resp ->
      int st = (resp?.status ?: 0) as int
      logInf("Reboot LAN solicitado em ${ip}: HTTP ${st}")
    }
  } catch (e){
    logWar("Falha ao solicitar reboot LAN em ${ip}: ${e}")
  }
  
}


/* MQTT COMMANDS FOR BOARD RESTART REMOTE /*
/** Conecta -> publica -> desconecta */
def sendReboot() {

    try { unschedule("publishPending") } catch (ignored) {}
    // operação pendente
    state.op = "publishOnce"
    state.pendingTopic   = "/molsmart/relay" + settings.molsmartSN + "/in/control"
    state.pendingPayload = PAYLOAD
    state.pendingQoS     = 0
    state.pendingRetain  = false
    state.connToken      = now()  // identifica esta tentativa

    try { interfaces.mqtt.disconnect() } catch (ignored) {}
    connectNow(state.connToken as Long)
    runIn(CONNECT_TIMEOUT_SEC, "abortIfNoConn")
}

private void connectNow(Long token) {
    String cid = "Hubitat-${device.id}-${token}"
    String uri = "tcp://${BROKER_HOST}:${BROKER_PORT}"
    try {
        interfaces.mqtt.connect(uri, cid, USERNAME, PASSWORD)
        if (debugLogs) log.debug "Conectando em ${uri} (clientId=${cid})..."
    } catch (e) {
        log.error "Erro ao conectar MQTT: ${e}"
        cleanupAfterError("connectFail")
    }
}

/** Recebe status do cliente MQTT */
def mqttClientStatus(String status) {
    if (debugLogs) log.debug "mqttClientStatus: ${status}"
    String s = status?.toLowerCase() ?: ""
    if (s.contains("connection succeeded")) {
        // Conectou: cancela timeout e publica após breve atraso
        try { unschedule("abortIfNoConn") } catch (ignored) {}
        runIn(1, "publishNowFromStatus")  // 1s é seguro em qualquer broker
    } else if (s.startsWith("error") || s.contains("disconnected")) {
        cleanupAfterError("status:${status}")
    }
}

/** Disparado logo após "connected" */
def publishNowFromStatus() {
    if (state.op != "publishOnce") {
        if (debugLogs) log.debug "Sem operação pendente para publicar."
        return
    }
    String topic   = state.pendingTopic
    String payload = state.pendingPayload
    int qos        = (state.pendingQoS ?: 0) as int
    boolean retain = (state.pendingRetain ?: false) as boolean

    try {
        // tentativa direta de publish; se algo ainda não estiver pronto, o driver lança exceção
        interfaces.mqtt.publish(topic, payload?.toString() ?: "", qos, retain)
        if (debugLogs) log.debug "PUBLISH -> topic='${topic}', qos=${qos}, retain=${retain}, payload='${payload}'"
    } catch (e) {
        log.error "Falha ao publicar em '${topic}': ${e}"
        cleanupAfterError("publishFail")
        return
    }

    // sucesso: limpar e desconectar
    state.clear()
    runIn(DISCONNECT_DELAY_SEC, "disconnectNow")
}

def disconnectNow() {
    try {
        interfaces.mqtt.disconnect()
        if (debugLogs) log.debug "MQTT: desconectado"
    } catch (e) {
        if (debugLogs) log.debug "MQTT: erro ao desconectar: ${e}"
    }
}

/** Timeout total da operação */
private void abortIfNoConn() {
    if (state.op == "publishOnce") {
        log.warn "Timeout de conexão MQTT (> ${CONNECT_TIMEOUT_SEC}s). Abortando envio."
        cleanupAfterError("timeout")
    }
}

/** Limpa estado e garante desconexão */
private void cleanupAfterError(String reason) {
    if (debugLogs) log.debug "Limpando estado por erro: ${reason}"
    state.clear()
    try { interfaces.mqtt.disconnect() } catch (ignored) {}
}

def logsOff(){
    log.warn "Desabilitando logs de debug automaticamente (30 min)."
    device.updateSetting("logEnable", [value:"false", type:"bool"])
}


private void noteBrokenPipeAndMaybeReboot(String origin){
    long nowMs = now()

    // Janela de contagem (2 minutos)
    long windowMs   = 2 * 60 * 1000L
    // Cooldown anti-loop de reboot (10 minutos)
    long cooldownMs = 10 * 60 * 1000L

    if (!state.bpFirstAt) state.bpFirstAt = nowMs

    // Estourou a janela → reseta contagem
    if ((nowMs - (state.bpFirstAt as Long)) > windowMs){
        state.bpFirstAt = nowMs
        state.bpCount = 0
    }

    state.bpCount = ((state.bpCount ?: 0) as Integer) + 1
    logWar("Broken pipe detectado (${state.bpCount}/5) [${origin}]")

    long lastRb = (state.lastAutoRebootAt ?: 0L) as Long

    if ((state.bpCount as Integer) >= 5 && (nowMs - lastRb) > cooldownMs){
        state.lastAutoRebootAt = nowMs

        // reseta contadores imediatamente
        state.bpCount = 0
        state.bpFirstAt = nowMs

        logWar("5x Broken pipe -> solicitando reboot automático do módulo (LAN)")

        try {
            // força matar o socket quebrado
            disconnectSocket()
        } catch(e) {
            logWar("Erro ao desconectar socket antes do reboot: ${e}")
        }

        try {
            sendRebootLAN()
        } catch(e) {
            logWar("Falha ao enviar reboot LAN: ${e}")
        }

        // dá tempo do módulo subir e refaz TODO o bootstrap
        runIn(30, "initialize", [overwrite: true])
    }
}





// Backward-compat: versões antigas agendavam hbAckCheck(). Mantemos um stub para evitar MissingMethodException.
def hbAckCheck(){ /* noop */ }
