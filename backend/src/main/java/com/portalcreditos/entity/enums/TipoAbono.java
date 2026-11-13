package com.portalcreditos.entity.enums;

/** Como se aplica un abono registrado por el administrador (pantalla "Abonos individuales"). */
public enum TipoAbono {
    /** Paga la mora y luego las cuotas en orden (seguros, interes y capital de cada cuota). */
    NORMAL,
    /** Cubre lo vencido y el excedente va a capital: se mantiene la cuota y se reduce el numero de cuotas. */
    CAPITAL_PLAZO,
    /** Cubre lo vencido y el excedente va a capital: se mantiene el numero de cuotas y baja el valor de la cuota. */
    CAPITAL_CUOTA,
    /** Cancela todo el credito a la fecha (saldo de capital + interes causado + seguros de la cuota en curso + mora). */
    TOTAL
}
