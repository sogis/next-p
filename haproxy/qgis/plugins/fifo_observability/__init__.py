def serverClassFactory(server_iface):
    from .plugin import FifoObservabilityPlugin

    return FifoObservabilityPlugin(server_iface)
