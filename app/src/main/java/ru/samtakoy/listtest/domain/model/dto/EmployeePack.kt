package ru.samtakoy.listtest.domain.model.dto

import ru.samtakoy.listtest.domain.model.Employee

class EmployeePack(
    val dataVersion: Int,
    val employees: List<Employee>
) {

    fun isEmpty() = employees.isEmpty()
}